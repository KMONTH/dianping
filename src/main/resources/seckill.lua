local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]

local stockKey = "seckill:stock:" .. voucherId
local orderKey = "seckill:order:" .. voucherId

if (tonumber(redis.call('get', stockKey)) <= 0) then
    return 1
end
if (redis.call('sadd', orderKey, userId)==0) then
    return 2
end
--redis.call('xadd','stream.orders','*','voucherId',voucherId,'userId',userId,'id',orderId)
redis.call('incrby',stockKey,-1)
return 0