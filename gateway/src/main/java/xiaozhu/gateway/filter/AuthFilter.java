package xiaozhu.gateway.filter;

import com.alibaba.fastjson2.JSON;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import xiaozhu.common.comm.HttpStatusEnum;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.common.constant.RedisKeyConstant;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 鉴权过滤器
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/16 15:13
 */
@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ReactiveRedisTemplate<String, String> redisTemplate;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    // 不需要鉴权的路径
    private static final List<String> SKIP_AUTH_PATHS = Arrays.asList(
            "/api/user/sendCode",
            "/api/user/loginOrRegister",
            "/api/user/getJWT",
            "/api/user/login",
            "/api/user/register",
            "/api/user/verify-code",
            "/api/user/reset-password"
    );

    public AuthFilter(ReactiveRedisTemplate<String, String> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // 检查是否需要跳过鉴权
        if (shouldSkipAuth(path)) {
            return chain.filter(exchange);
        }

        // 获取 Token
        String token = getToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorizedResponse(exchange, "未提供认证令牌");
        }

        // 验证 Token
        try {
            Claims claims = parseToken(token);
            if (claims == null) {
                return unauthorizedResponse(exchange, "无效的认证令牌");
            }

            // 检查 Token 是否在 Redis 中（可选，用于实现登出功能）
            String userId = claims.getSubject();
            return redisTemplate.hasKey(RedisKeyConstant.USER_TOKEN_PREFIX + userId)
                    .flatMap(exists -> {
                        if (Boolean.TRUE.equals(exists)) {
                            // Token 有效，将用户信息添加到请求头中
                            ServerHttpRequest modifiedRequest = request.mutate()
                                    .header("X-User-Id", userId)
                                    .header("X-Username", claims.get("username", String.class))
                                    .build();
                            return chain.filter(exchange.mutate().request(modifiedRequest).build());
                        } else {
                            return unauthorizedResponse(exchange, "认证令牌已失效");
                        }
                    })
                    .onErrorResume(e -> {
                        log.error("Redis 查询失败", e);
                        // Redis 查询失败时，仍然验证 JWT 本身
                        ServerHttpRequest modifiedRequest = request.mutate()
                                .header("X-User-Id", userId)
                                .header("X-Username", claims.get("username", String.class))
                                .build();
                        return chain.filter(exchange.mutate().request(modifiedRequest).build());
                    });
        } catch (Exception e) {
            log.error("Token 验证失败", e);
            return unauthorizedResponse(exchange, "认证令牌验证失败");
        }
    }

    /**
     * 检查路径是否需要跳过鉴权
     */
    private boolean shouldSkipAuth(String path) {
        return SKIP_AUTH_PATHS.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 从请求头中获取 Token
     */
    private String getToken(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 解析 JWT Token
     */
    private Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            log.error("Token 解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 返回未授权响应
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().add("Content-Type", "application/json;charset=UTF-8");

        ResponseResult<Object> result = ResponseResult.fail(HttpStatusEnum.UNAUTHORIZED.getCode(), message);
        String json = JSON.toJSONString(result);
        DataBuffer buffer = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    @Override
    public int getOrder() {
        return -100; // 优先级较高，在其他过滤器之前执行
    }
}
