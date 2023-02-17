package com.hmdp.utils;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.RedisData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.util.annotation.Nullable;

import java.time.LocalDateTime;

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

    @Autowired
    public RedisUtil(RedisTemplate redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
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
}
