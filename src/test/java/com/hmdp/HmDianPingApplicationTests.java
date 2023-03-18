package com.hmdp;

import org.junit.jupiter.api.Test;
import org.redisson.RedissonMultiLock;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    private RedissonClient redissonClient;


    @Test
    public void testRedisson() throws Exception {

        //获取锁(可重入)，指定锁的名称
        RLock rlock = redissonClient.getLock("anyLock");
        RedissonMultiLock multiLock = (RedissonMultiLock) redissonClient.getMultiLock();
        multiLock.tryLock();
        //尝试获取锁，参数分别是：获取锁的最大等待时间(期间会重试)，锁自动释放时间，时间单位
        boolean isLock = rlock.tryLock(1, 10, TimeUnit.SECONDS);
//        rlock.lock
        rlock.unlock();
        boolean isLock1 = rlock.tryLock();
        //判断获取锁成功
        if (isLock) {
            try {
                System.out.println("执行业务");
            } finally {
                //释放锁
                rlock.unlock();
            }
        }
    }

    @Test
    public void test() {

        long l = 61;
        while ((l & 1) == 1) {
            System.out.println(Long.toBinaryString(l));
            l = l >> 1;
        }
    }

}
