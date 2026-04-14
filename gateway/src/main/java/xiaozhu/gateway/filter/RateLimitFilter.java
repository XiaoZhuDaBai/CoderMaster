package xiaozhu.gateway.filter;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.common.constant.RedisKeyConstant;

import java.nio.charset.StandardCharsets;
import java.time.Duration;


/**
 * 限流过滤器
 * 基于 Redis 的滑动窗口限流算法
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/16 15:15
 */
@Slf4j
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    private final ReactiveRedisTemplate<String, String> redisTemplate;

    // 限流配置：每秒允许的请求数
    private static final int RATE_LIMIT_PER_SECOND = 10;
    // 限流时间窗口（秒）
    private static final int TIME_WINDOW_SECONDS = 1;
    // 限流键前缀
    private static final String RATE_LIMIT_KEY_PREFIX = RedisKeyConstant.RATE_LIMIT_PREFIX;

    public RateLimitFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // 获取限流键（基于 IP 或用户 ID）
        String rateLimitKey = getRateLimitKey(request);

        // 执行限流检查
        return checkRateLimit(rateLimitKey)
                .flatMap(allowed -> {
                    if (allowed) {
                        // 允许通过，添加限流响应头
                        ServerHttpResponse response = exchange.getResponse();
                        response.getHeaders().add("X-RateLimit-Remaining",
                                String.valueOf(RATE_LIMIT_PER_SECOND - 1));
                        return chain.filter(exchange);
                    } else {
                        // 限流，返回 429 Too Many Requests
                        return rateLimitResponse(exchange);
                    }
                })
                .onErrorResume(e -> {
                    log.error("限流检查失败", e);
                    // 限流检查失败时，允许通过（降级策略）
                    return chain.filter(exchange);
                });
    }

    /**
     * 获取限流键
     * 优先使用用户 ID，否则使用 IP 地址
     */
    private String getRateLimitKey(ServerHttpRequest request) {
        // 尝试从请求头获取用户 ID
        String userId = request.getHeaders().getFirst("X-User-Id");
        if (userId != null && !userId.isEmpty()) {
            return RATE_LIMIT_KEY_PREFIX + "user:" + userId;
        }

        // 使用 IP 地址
        String ip = getClientIp(request);
        return RATE_LIMIT_KEY_PREFIX + "ip:" + ip;
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(ServerHttpRequest request) {
        String ip = request.getHeaders().getFirst("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeaders().getFirst("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddress() != null ?
                    request.getRemoteAddress().getAddress().getHostAddress() : "unknown";
        }
        // 处理多级代理的情况
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 检查是否允许通过（计数器限流算法）
     * 使用Redis的INCR和过期时间实现简单的计数器限流
     */
    private Mono<Boolean> checkRateLimit(String key) {
        // 使用 Redis 的 INCR 和 EXPIRE 实现计数器限流
        // 每次请求都增加计数器，并尝试设置过期时间
        return redisTemplate.opsForValue().increment(key)
                .flatMap(count -> {
                    // 尝试设置过期时间（即使不是第一次访问，也要确保有过期时间）
                    // 这是为了处理Redis重启或其他异常情况导致的过期时间丢失
                    return redisTemplate.expire(key, Duration.ofSeconds(TIME_WINDOW_SECONDS))
                            .map(expired -> count)
                            .defaultIfEmpty(count); // 如果设置过期失败，继续使用当前计数
                })
                .map(count -> {
                    // 检查是否超过限制
                    if (count > RATE_LIMIT_PER_SECOND) {
                        // 超过限制，返回false
                        // 计数器会自然过期，不需要手动减少计数
                        return false;
                    }
                    // 在限制范围内，允许通过
                    return true;
                })
                .defaultIfEmpty(true); // Redis 操作失败时默认允许通过
    }

    /**
     * 返回限流响应
     */
    private Mono<Void> rateLimitResponse(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");
        response.getHeaders().add("Retry-After", String.valueOf(TIME_WINDOW_SECONDS));

        ResponseResult<Object> result = ResponseResult.fail(429, "请求过于频繁，请稍后再试");
        String json = JSON.toJSONString(result);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -50; // 在鉴权过滤器之后执行
    }
}