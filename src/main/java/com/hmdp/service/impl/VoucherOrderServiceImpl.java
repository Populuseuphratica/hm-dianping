package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisLock;
import com.hmdp.utils.UserHolder;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    // 读取lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    // 阻塞队列
    private static final BlockingQueue<VoucherOrder> BLOCKINGQUEUE;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("/lua/seckill.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);

        BLOCKINGQUEUE = new ArrayBlockingQueue(1024 * 1024);
    }


    private final ISeckillVoucherService seckillVoucherService;

    private RedisLock redisLock;

    private RedisTemplate redisTemplate;

    // 线程池
    private ThreadPoolTaskExecutor taskExecutor;

    // 在另一个线程中调用本类事务方法用
    @Autowired
    private VoucherOrderServiceImpl voucherOrderServiceImpl;

    @Autowired
    public VoucherOrderServiceImpl(ISeckillVoucherService seckillVoucherService, RedisLock redisLock, RedissonClient redissonClient, RedisTemplate redisTemplate, @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.seckillVoucherService = seckillVoucherService;
        this.redisLock = redisLock;
        this.redisTemplate = redisTemplate;
        this.taskExecutor = taskExecutor;
    }

    /**
     * @Description: 当bean被创建时就开始执行任务，另开线程读取阻塞队列中的任务执行<br/>
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/10 20:34 <br/>
     * @param: <br/>
     * @Return: void <br/>
     * @Throws:
     */
    @PostConstruct
    public void doTask() {

        taskExecutor.execute(() -> {
            // TODO 引入MQ后替换
            while (true) {
                voucherOrderServiceImpl.createOrderForAsync();
            }
        });

    }

    /**
     * @Description: <br/>
     * @Author: sanyeshu <br/>
     * @Date: 2023/2/22 15:54 <br/>
     * @param: Long voucherId <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @Override
    public Result seckillVoucher(Long voucherId) {

        return seckillVoucherAsync(voucherId);
    }

    /**
     * @Description: 异步实现秒杀<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/10 20:33 <br/>
     * @param: Long voucherId <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    public Result seckillVoucherAsync(Long voucherId) {
        // 用户id
        Long userId = UserHolder.getUser().getId();
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);

        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }

        // 调用lua脚本，在redis中判断库存和一人一单，成功则扣减库存，返回0l
        Long luaResult = (Long) redisTemplate.execute(
                UNLOCK_SCRIPT,
                Arrays.asList(RedisConstants.SECKILL_STOCK_KEY, RedisConstants.SECKILL_ORDER_KEY),
                voucherId, userId
        );

        if (luaResult != 0l) {
            if (luaResult == 1l) {
                return Result.fail("秒杀券库存不足");
            } else {
                return Result.fail("您已购买过此券，不能再次购买");
            }
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.使用雪花算法创建订单id
        long orderId = IdWorker.getId();
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        // 插入阻塞队列
        boolean offer = BLOCKINGQUEUE.offer(voucherOrder);

        if (offer) {
            // 7.返回订单id
            return Result.ok(orderId);
        } else {
            return Result.fail("当前业务繁忙，请稍后再试");
        }
    }

    @Transactional
    public void createOrderForAsync() {
        VoucherOrder voucherOrder = null;
        try {
            voucherOrder = BLOCKINGQUEUE.take();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        // 存储订单
        save(voucherOrder);

        // 扣减库存，因为有redis保证，所以不用再次加锁
        seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherOrder.getVoucherId()).update();
    }

    public Result seckillVoucherEasy(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        // 5.一人一单逻辑
        // 5.1.用户id
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        //6，扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock= stock -1")
                .eq("voucher_id", voucherId).gt("stock", 0).update(); //where id = ? and stock > 0
        if (!success) {
            //扣减库存
            return Result.fail("库存不足！");
        }
        //7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.使用雪花算法创建订单id
        long orderId = IdWorker.getId();
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }

    public Result seckillVoucherWithRedisLock(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        // 5.一人一单逻辑
        // 5.1.用户id
        Long userId = UserHolder.getUser().getId();
        boolean lock = redisLock.lock(userId.toString() + voucherId, 30L, TimeUnit.SECONDS);
        if (!lock) {
            return Result.fail("本商品一人限购一件，请勿重复抢购");
        }

        try {
            // 创建本类的代理对象，用代理对象去调用本类的方法（注意调用的方法要在接口中有）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 5.一人一单逻辑
            // 判断订单是否存在-扣减优惠券库存-创建订单
            return proxy.createVoucherOrder(voucherId);
        } finally {
            redisLock.unLock(userId.toString());
        }
    }

    /**
     * @Description: 一人只能抢一张的优惠券<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/2/27 14:13 <br/>
     * @param: Long voucherId <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @Override
    public Result seckillVoucherOnlyOne(Long voucherId) {
        // 1.查询优惠券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        // 2.判断秒杀是否开始
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀尚未开始！");
        }
        // 3.判断秒杀是否已经结束
        if (voucher.getEndTime().isBefore(LocalDateTime.now())) {
            // 尚未开始
            return Result.fail("秒杀已经结束！");
        }
        // 4.判断库存是否充足
        if (voucher.getStock() < 1) {
            // 库存不足
            return Result.fail("库存不足！");
        }
        Long userId = UserHolder.getUser().getId();

        // spring 事务是用代理对象来实现的，但是在容器中调用本类的方法不是用代理对象，所以其调用方法的事务会失效。
        synchronized (userId.toString().intern()) {
            // 创建本类的代理对象，用代理对象去调用本类的方法（注意调用的方法要在接口中有）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            // 5.一人一单逻辑
            return proxy.createVoucherOrder(voucherId);
        }
    }

    /**
     * @Description: 判断订单-扣减库存-创建订单<br/>
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/9 16:43 <br/>
     * @param: Long voucherId <br/>
     * @Return: com.hmdp.dto.Result <br/>
     * @Throws:
     */
    @Transactional
    @Override
    public Result createVoucherOrder(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 5.1.查询订单
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        // 5.2.判断是否存在
        if (count > 0) {
            // 用户已经购买过了
            return Result.fail("用户已经购买过一次！");
        }

        // 6.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1") // set stock = stock - 1
                .eq("voucher_id", voucherId).gt("stock", 0) // where id = ? and stock > 0
                .update();
        if (!success) {
            // 扣减失败
            return Result.fail("库存不足！");
        }

        // 7.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 7.1.使用雪花算法创建订单id
        long orderId = IdWorker.getId();
        voucherOrder.setId(orderId);
        // 7.2.用户id
        voucherOrder.setUserId(userId);
        // 7.3.代金券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        // 7.返回订单id
        return Result.ok(orderId);
    }
}
