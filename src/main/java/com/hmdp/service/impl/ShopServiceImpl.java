package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.RedisData;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Qualifier("taskExecutor")
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    private RedisUtil redisUtil;

    // @Override
    public Result queryByIdBak(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查询商家缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 如果是缓存的空值，直接返回。--缓存空对象避免缓存穿透
        if ("".equals(shopJson)) {
            return Result.fail("商铺不存在");
        }

        // 判新是否存在
        if (shopJson != null) {
            // 存在，直凌返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return Result.ok(shop);
        }

        // 不存在，根据id查询数据库
        // ----互斥锁避免缓存击穿
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);
        Shop shop = null;
        try {
            // 得到锁，搜索数据存入缓存
            if (lock) {
                shop = getById(id);
                // 数据库中不存在，往redis中存入空值，设置2分钟的有效时间。--缓存空对象避免缓存穿透
                if (shop == null) {
                    stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return Result.fail("商铺不存在");
                }
                System.out.println("--------------------走了数据库---------------");
                // 存在，写入redis，30分钟有效时间
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                // 返回商铺信息
                return Result.ok(shop);
            } else {
                // 没得到锁，等待一段时间后重试
                Thread.sleep(50);
                return queryById(id);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //释放锁
            stringRedisTemplate.delete(lockKey);
        }

        // 如果没查询到返回错误
        return Result.fail("商品不存在");
    }

    @Override
    public Result queryById(Long id) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查询商家缓存
        String redisJson = stringRedisTemplate.opsForValue().get(key);

        RedisData<Shop> redisData = JSONUtil.toBean(redisJson, RedisData.class);
        LocalDateTime expiryTime = null;
        LocalDateTime now = LocalDateTime.now();
        // 如果缓存中数据为null或者存在null字段
        if (BeanUtil.hasNullField(redisData)) {

            // // 从数据库中查询数据
            // Shop shop = getById(id);
            // if (shop == null) {
            //     return Result.fail("商铺不存在");
            // }
            // // 设置过期时间并存入redis中
            // expiryTime = now.plusDays(RedisConstants.CACHE_SHOP_TTL);
            //
            // redisUtil.setJsonOfLogicExpire(key, shop, expiryTime);
            // // 返回数据
            // return Result.ok(shop);
            return Result.fail("商铺不存在");
        }

        // 查看当前缓存是否过期
        expiryTime = redisData.getExpiryTime();

        if (expiryTime.isAfter(now)) {
            // 如果没过期，返回数据
            Shop shop = redisData.getData();
            return Result.ok(shop);
        }

        // 如果过期，新开线程根据id查询数据库并更新缓存
        // 互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);

        try {
            // 得到锁，开启另一个线程搜索数据存入缓存
            if (lock) {
                taskExecutor.execute(() -> {
                    Shop shop = getById(id);
                    System.out.println("--------------------走了数据库---------------");
                    // if (shop == null) {
                    //     // 数据库中不存在，往redis中存入空值，设置2分钟的有效时间。--缓存空对象避免缓存穿透
                    //     //TODO
                    //     stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    // }
                    // 设置过期时间并存入redis中
                    LocalDateTime expiry = now.plusDays(RedisConstants.CACHE_SHOP_TTL);
                    redisUtil.setJsonOfLogicExpire(key, shop, expiry);
                });
            }
        } finally {
            //释放锁
            stringRedisTemplate.delete(lockKey);
        }

        Shop shop = redisData.getData();
        // 返回结果
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result updateShopById(Shop shop) {

        Long id = shop.getId();
        if (id == null) {
            return Result.fail("商铺不存在");
        }

        // 1.更新数据库
        updateById(shop);

        // 2.删除redis中缓存
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        stringRedisTemplate.delete(key);

        return Result.ok();
    }
}
