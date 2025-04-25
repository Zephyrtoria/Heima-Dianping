-- 1. 参数列表
-- 优惠卷id
local voucherId = ARGV[1]
-- 用户id
local userId = ARGV[2]

-- 2. 数据key
-- 库存key
local stockKey = "seckill:stock:" .. voucherId
-- 订单key
local orderKey = "seckill:order:" .. voucherId

-- 3. 业务脚本
-- 判断库存是否充足，注意redis.call(get)获取的为字符串，要进行转换
if (tonumber(redis.call("get", stockKey)) <= 0) then
    -- 库存不足，返回1
    return 1
end
-- 判断用户是否已经下过订单
-- SISMEMBER 0 不存在；1 不存在
if (redis.call("sismember", orderKey, userId) == 1) then
    -- 存在，说明是重复下单，返回2
    return 2
end
-- 扣减库存，下单，保存用户
redis.call("incrby", stockKey, -1)
redis.call("sadd", orderKey, userId)
return 0