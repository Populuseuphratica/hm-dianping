package com.hmdp.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * @Description: 逻辑过期-redis数据
 * @Auther: Kill_Stan
 * @Date: 2023/2/9 15:34
 * @Version: v1.0
 */
@Data
public class RedisData<T> {
    private LocalDateTime expiryTime;
    private T data;
}
