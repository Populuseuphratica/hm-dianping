package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import reactor.util.annotation.Nullable;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * @Description: Redis 使用工具类
 * @Auther: Kill_Stan
 * @Date: 2023/2/10 13:36
 * @Version: v1.0
 */
@Component
public class RedisUtil {

    private final RedisTemplate redisTemplate;

    private final StringRedisTemplate stringRedisTemplate;

    private final ThreadPoolTaskExecutor taskExecutor;

    @Autowired
    public RedisUtil(RedisTemplate redisTemplate, StringRedisTemplate stringRedisTemplate, @Qualifier("taskExecutor") ThreadPoolTaskExecutor taskExecutor) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.taskExecutor = taskExecutor;
    }

    /**
     * @Description: 将对象存入redis中，并设置过期时间
     * @Param: [key, object, expireTime, timeUnit]
     * @Return: void
     * @Author Kill_Stan
     * @Date 2023/2/10 21:38
     */
    public <T> void setObjectWithExpire(String key, @Nullable T object, Long expireTime, TimeUnit timeUnit) {
        redisTemplate.opsForValue().set(key, object, expireTime, timeUnit);
    }

    /**
     * @Description: 利用逻辑过期，将对象存入redis中
     * @Param: [key, object, expireTime]
     * @Return: void
     * @Author Kill_Stan
     * @Date 2023/2/10 21:38
     */
    public <T> void setObjectOfLogicExpire(String key, @Nullable T object, LocalDateTime expireTime) {
        RedisData<T> redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpiryTime(expireTime);
        redisTemplate.opsForValue().set(key, redisData);
    }

    /**
     * @Description: 利用逻辑过期，将对象转和过期时间封装为 RedisData ，转为json存入redis中
     * @Param: [key, object, expireTime]
     * @Return: void
     * @Author Kill_Stan
     * @Date 2023/2/10 21:40
     */
    public <T> void setJsonOfLogicExpire(String key, @Nullable T object, LocalDateTime expireTime) {
        RedisData<T> redisData = new RedisData();
        redisData.setData(object);
        redisData.setExpiryTime(expireTime);
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    /**
     * @Description: 根据id查询，空值解决缓存穿透<br/>
     * @Author: sanyeshu <br/>
     * @Date: 2023/2/20 14:17 <br/>
     * @Param: <br/>
     * @Return:
     */
    public <R, T> R queryByIdWithPassThrough(T id, Class<R> returnClass, Function<T, R> doQuery) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查询商家缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        // 如果是缓存的空值，直接返回。--缓存空对象避免缓存穿透
        if ("".equals(shopJson)) {
            return null;
        }

        // 判新是否存在
        if (shopJson != null) {
            // 存在，直凌返回
            R returnData = JSONUtil.toBean(shopJson, returnClass);
            return returnData;
        }

        // 不存在，根据id查询数据库
        // ----互斥锁避免缓存击穿
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean lock = tryLock(lockKey);
        R returnData = null;
        try {
            // 得到锁，搜索数据存入缓存
            if (lock) {
                Object apply = doQuery.apply(id);
                // 数据库中不存在，往redis中存入空值，设置2分钟的有效时间。--缓存空对象避免缓存穿透
                if (apply == null) {
                    stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    return null;
                }
                System.out.println("--------------------走了数据库---------------");
                // 存在，写入redis，30分钟有效时间
                returnData = (R) doQuery.apply(id);
                stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(returnData), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
                // 返回商铺信息
                return returnData;
            } else {
                // 没得到锁，等待一段时间后重试
                Thread.sleep(50);
                return queryByIdWithPassThrough(id, returnClass, doQuery);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            //释放锁
            stringRedisTemplate.delete(lockKey);
        }

        // 如果没查询到返回错误
        return null;
    }

    /**
     * @Description: 根据id查询信息，使用redis缓存且避免缓存击穿 <br/>
     * @Author: sanyeshu <br/>
     * @Date: 2023/2/20 19:31 <br/>
     * @param: T id
     * @param: Class<R> returnClass
     * @param: Function<T doQuery < br />
     * @Return: R
     * @Throws:
     */
    public <R, T> R queryByIdOfLogicExpire(T id, Class<R> returnClass, Function<T, R> doQuery) {
        String key = RedisConstants.CACHE_SHOP_KEY + id;
        // redis 查询商家缓存
        String redisJson = stringRedisTemplate.opsForValue().get(key);

        RedisData<JSONObject> redisData = JSONUtil.toBean(redisJson, RedisData.class);
        LocalDateTime expiryTime = null;
        LocalDateTime now = LocalDateTime.now();
        // 如果缓存中数据为null或者存在null字段
        if (BeanUtil.hasNullField(redisData)) {
//            // 从数据库中查询数据
//            R returnDate = doQuery.apply(id);
//            if (shop == null) {
//                return Result.fail("商铺不存在");
//            }
//            // 设置过期时间并存入redis中
//            expiryTime = now.plusSeconds(RedisConstants.CACHE_NULL_TTL);
//
//            redisUtil.setJsonOfLogicExpire(key, shop, expiryTime);
//            // 返回数据
//            return Result.ok(shop);
            return null;
        }

        // 查看当前缓存是否过期
        expiryTime = redisData.getExpiryTime();

        if (expiryTime.isAfter(now)) {
            // 如果没过期，返回数据
            R returnData = JSONUtil.toBean(redisData.getData(), returnClass);

            return returnData;
        }

        // 如果过期，新开线程根据id查询数据库并更新缓存
        // 互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        Boolean lock = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", RedisConstants.LOCK_SHOP_TTL, TimeUnit.SECONDS);

        try {
            // 得到锁，开启另一个线程搜索数据存入缓存
            if (lock) {
                taskExecutor.execute(() -> {
                    R returnData = doQuery.apply(id);
                    // if (shop == null) {
                    //     // 数据库中不存在，往redis中存入空值，设置2分钟的有效时间。--缓存空对象避免缓存穿透
                    //     //TODO
                    //     stringRedisTemplate.opsForValue().set(key, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                    // }
                    // 设置过期时间并存入redis中
                    LocalDateTime expiry = now.plusSeconds(RedisConstants.CACHE_SHOP_TTL);
                    setJsonOfLogicExpire(key, returnData, expiry);
                });
            }
        } finally {
            //释放锁
            stringRedisTemplate.delete(lockKey);
        }

        R returnData = JSONUtil.toBean(redisData.getData(), returnClass);
        // 返回结果
        return returnData;
    }

    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(flag);
    }
}
