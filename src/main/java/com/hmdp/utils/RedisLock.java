package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * @Description: <br/>
 * @Date: 2023/3/1 20:38 <br/>
 * @Author sanyeshu <br/>
 * @Version 1.0
 */
@Component
public class RedisLock {

    private final static String PERFIX_LOCK = "lock:";
    // 设置成静态属性，这样每一个jvm的前缀是一致的
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";

    private final StringRedisTemplate stringRedisTemplate;

    public RedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @Description: 获取锁<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/2 10:27 <br/>
     * @param: String key 锁的key（用来实现互斥）
     * @param: Long expireTime
     * @param: TimeUnit timeUnit <br/>
     * @Return: boolean <br/>
     * @Throws:
     */
    public boolean lock(String key, Long expireTime, TimeUnit timeUnit) {
        ValueOperations<String, String> stringStringValueOperations = stringRedisTemplate.opsForValue();
        long id = Thread.currentThread().getId();
        Boolean lock = stringStringValueOperations.setIfAbsent(PERFIX_LOCK + key, ID_PREFIX + id, expireTime, timeUnit);
        return Boolean.TRUE.equals(lock);
    }

    /**
     * @Description: 释放锁<br />
     * @Author: sanyeshu <br/>
     * @Date: 2023/3/2 10:28 <br/>
     * @param: String key <br/>
     * @Return: void <br/>
     * @Throws:
     */
    public void unLock(String key) {

        // 获取线程id
        long id = Thread.currentThread().getId();

        // 获取线程标示
        String threadId = ID_PREFIX + Thread.currentThread().getId();

        // 判断是否是自己的锁，是则释放
        String redisKey = PERFIX_LOCK + key;
        String s = stringRedisTemplate.opsForValue().get(redisKey);
        if (threadId.equals(s)) {
            stringRedisTemplate.delete(redisKey);
        }
    }
}
