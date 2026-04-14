package xiaozhu.problem.service.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 通用 Cache-Aside 模式模板
 * 提供 Redis 缓存读写逻辑，任何需要 Cache-Aside 模式的场景均可复用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CacheAsideTemplate {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * Cache-Aside 模式读取：
     * 1. 先查 Redis，命中则返回
     * 2. 未命中则调用 dbLoader 回源，回写 Redis 后返回
     *
     * @param redisKey    Redis key
     * @param dbLoader    数据库加载器（未命中时调用）
     * @param entityClass 实体类型（用于日志）
     * @param ttl         缓存过期时间
     * @param timeUnit    时间单位
     * @return 查询结果，未命中且 dbLoader 返回 null 时返回 null
     */
    public <T> T get(String redisKey, Supplier<T> dbLoader, Class<T> entityClass, long ttl, TimeUnit timeUnit) {
        return get(redisKey, dbLoader, entityClass, null, ttl, timeUnit);
    }

    /**
     * Cache-Aside 模式读取（带条件判断）：
     * 1. 先查 Redis，命中则返回
     * 2. 未命中则调用 dbLoader 回源，回写 Redis 后返回
     * 3. 若 resultCondition 不为 null 且返回 false，则不写入缓存
     *
     * @param redisKey         Redis key
     * @param dbLoader         数据库加载器
     * @param entityClass      实体类型
     * @param resultCondition  结果条件判断（可为 null），返回 false 时跳过回写
     * @param ttl              缓存过期时间
     * @param timeUnit         时间单位
     * @return 查询结果
     */
    public <T> T get(String redisKey, Supplier<T> dbLoader, Class<T> entityClass,
                    java.util.function.Predicate<T> resultCondition, long ttl, TimeUnit timeUnit) {
        // 1. 查 Redis
        try {
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached != null) {
                log.debug("[Cache-Aside] Redis 命中，key={}", redisKey);
                if (entityClass.isInstance(cached)) {
                    return entityClass.cast(cached);
                }
                // 类型不匹配，视为缓存损坏，降级到数据库
                log.warn("[Cache-Aside] Redis 数据类型不匹配，降级到数据库，key={}, expected={}",
                        redisKey, entityClass.getSimpleName());
            }
        } catch (Exception e) {
            log.warn("[Cache-Aside] Redis 查询失败，降级到数据库，key={}, error={}", redisKey, e.getMessage());
        }

        // 2. 未命中，回源
        log.debug("[Cache-Aside] Redis 未命中，查询数据库，key={}", redisKey);
        T result = dbLoader.get();
        if (result == null) {
            log.debug("[Cache-Aside] 数据库也未命中，key={}", redisKey);
            return null;
        }

        // 3. 条件判断通过后回写 Redis
        if (resultCondition == null || resultCondition.test(result)) {
            try {
                redisTemplate.opsForValue().set(redisKey, result, ttl, timeUnit);
                log.debug("[Cache-Aside] 缓存已回写，key={}, ttl={} {}", redisKey, ttl, timeUnit.name());
            } catch (Exception e) {
                log.warn("[Cache-Aside] 缓存回写失败（不影响主流程），key={}, error={}", redisKey, e.getMessage());
            }
        } else {
            log.debug("[Cache-Aside] 条件不满足，跳过缓存回写，key={}", redisKey);
        }

        return result;
    }
}
