package xiaozhu.common.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * Redis 滑动窗口限流器
 * 使用 Redis ZSet + Lua脚本 实现滑动窗口限流算法
 * 确保原子性操作，避免高并发下的竞态条件
 *
 * @author XiaoZhuDaBai
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSlidingWindowRateLimiter {

    private final RedisTemplate<String, Object> redisTemplate;

    private DefaultRedisScript<Long> rateLimitScript;

    private static final String RATE_LIMIT_KEY_PREFIX = "rate_limit:sliding_window:";

    /**
     * 初始化Lua脚本
     */
    @PostConstruct
    public void init() {
        rateLimitScript = new DefaultRedisScript<>();
        rateLimitScript.setResultType(Long.class);
        rateLimitScript.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("lua/sliding_window_rate_limiter.lua")));
        log.info("滑动窗口限流Lua脚本加载成功");
    }

    /**
     * 检查是否允许请求通过（滑动窗口算法）
     * 使用Lua脚本保证原子性
     *
     * @param key 限流键（通常是用户ID或IP）
     * @param windowSizeInSeconds 时间窗口大小（秒）
     * @param maxRequests 时间窗口内允许的最大请求数
     * @return true 允许通过，false 限流
     */
    public boolean tryAcquire(String key, int windowSizeInSeconds, int maxRequests) {
        try {
            String zsetKey = RATE_LIMIT_KEY_PREFIX + key;
            long currentTime = System.currentTimeMillis();
            long windowSizeInMillis = windowSizeInSeconds * 1000L;

            // 执行Lua脚本，保证原子性
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList(zsetKey),
                    String.valueOf(currentTime),
                    String.valueOf(windowSizeInMillis),
                    String.valueOf(maxRequests)
            );

            // Lua脚本返回1表示允许通过，0表示限流
            if (result != null && result == 1) {
                return true;
            } else {
                log.debug("限流触发 - key: {}, windowSize: {}s, maxRequests: {}",
                        key, windowSizeInSeconds, maxRequests);
                return false;
            }
        } catch (Exception e) {
            log.error("限流检查失败 - key: {}", key, e);
            // 限流检查失败时，默认允许通过（降级策略）
            return true;
        }
    }

    /**
     * 获取当前窗口内的请求数
     * 
     * @param key 限流键
     * @param windowSizeInSeconds 时间窗口大小（秒）
     * @return 当前窗口内的请求数
     */
    public long getCurrentRequestCount(String key, int windowSizeInSeconds) {
        try {
            long currentTime = System.currentTimeMillis();
            long windowStart = currentTime - (windowSizeInSeconds * 1000L);
            
            String zsetKey = "rate_limit:sliding_window:" + key;
            
            // 移除窗口外的旧数据
            redisTemplate.opsForZSet().removeRangeByScore(zsetKey, 0, windowStart);
            
            // 获取当前窗口内的请求数
            Long count = redisTemplate.opsForZSet().count(zsetKey, windowStart, currentTime);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("获取请求数失败 - key: {}", key, e);
            return 0;
        }
    }

    /**
     * 获取剩余可请求次数
     * 
     * @param key 限流键
     * @param windowSizeInSeconds 时间窗口大小（秒）
     * @param maxRequests 时间窗口内允许的最大请求数
     * @return 剩余可请求次数
     */
    public long getRemainingRequests(String key, int windowSizeInSeconds, int maxRequests) {
        long currentCount = getCurrentRequestCount(key, windowSizeInSeconds);
        return Math.max(0, maxRequests - currentCount);
    }
}

