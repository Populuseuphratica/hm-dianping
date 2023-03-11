-- 1.参数列表
-- 1.1.优惠券id
local voucherId = ARGV[1]
-- 1.2.用户id
local userId = ARGV[2]
-- 1.3.订单id
--local orderId = ARGV[3]

-- 2.数据key
-- 2.1.库存key
--local stockKey = KEYS[1]
local stockKey = KEYS[1] .. voucherId
-- 2.2.订单key
--local orderKey = KEYS[2]
local orderKey = KEYS[2] .. voucherId

--3.判断库存
if (tonumber(redis.call('get', stockKey)) < 1) then
    -- 库存不足，返回1
    return 1
end

--4.检验是否一人一单
if (redis.call('sismember', orderKey, userId) == 1) then
    -- 已经购买过,返回2
    return 2
end

--5.扣减库存
redis.call('incrby', stockKey, -1)
--6.将user加入集合
redis.call('sadd', orderKey, userId)
--redis.call('xadd', 'stream.orders', '*', 'userId', userId, 'voucherId', voucherId, 'id', orderId)
--7.成功返回0
return 0
