-- 滑动窗口限流 Lua 脚本
-- 参数: KEYS[1]=限流键, ARGV[1]=当前时间戳, ARGV[2]=窗口大小(毫秒), ARGV[3]=最大请求数

local key = KEYS[1]
local currentTime = tonumber(ARGV[1])
local windowSize = tonumber(ARGV[2])
local maxRequests = tonumber(ARGV[3])

local windowStart = currentTime - windowSize

-- 1. 移除窗口外的旧数据
redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)

-- 2. 获取当前窗口内的请求数
local currentCount = redis.call('ZCARD', key)

-- 3. 检查是否超过限制
if currentCount >= maxRequests then
    return 0
end

-- 4. 添加当前请求（使用时间戳+随机数确保唯一性）
local member = currentTime .. ':' .. math.random(1000, 9999)
redis.call('ZADD', key, currentTime, member)

-- 5. 设置过期时间
redis.call('EXPIRE', key, math.ceil(windowSize / 1000) + 1)

return 1
