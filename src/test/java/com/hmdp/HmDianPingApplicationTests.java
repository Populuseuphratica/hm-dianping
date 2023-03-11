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
    public void testMultiLock(RedissonClient redisson1,
                              RedissonClient redisson2, RedissonClient redisson3) {

        // 分别用各服务器的 redisson 获取一个锁
        RLock lock1 = redisson1.getLock("lock1");
        RLock lock2 = redisson2.getLock("lock2");
        RLock lock3 = redisson3.getLock("lock3");

        // 将各个锁设置为一个连锁
        RedissonMultiLock lock = new RedissonMultiLock(lock1, lock2, lock3);

        try {
            // 同时加锁：lock1 lock2 lock3, 所有的锁都上锁成功才算成功。
            lock.lock();

            // 尝试加锁，最多等待100秒，上锁以后10秒自动解锁
            boolean res = lock.tryLock(100, 10, TimeUnit.SECONDS);

        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.unlock();
        }

    }
}
