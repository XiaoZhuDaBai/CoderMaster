package xiaozhu.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 全局过滤器
 * 用于请求日志记录、请求/响应修改等
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/16 15:16
 */
@Slf4j
@Component
public class MyGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        ServerHttpResponse response = exchange.getResponse();

        long startTime = System.currentTimeMillis();
        String path = request.getURI().getPath();
        String method = request.getMethod().name();
        String clientIp = getClientIp(request);

        log.info("请求开始 - IP: {}, Method: {}, Path: {}", clientIp, method, path);

        // 添加响应头
        response.getHeaders().add("X-Gateway-Version", "1.0");
        response.getHeaders().add("X-Request-Id", java.util.UUID.randomUUID().toString());
        
        // 添加网关来源标识，供后端服务验证请求来源
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-Gateway-Request", "true")
                .header("X-Gateway-Request-Id", java.util.UUID.randomUUID().toString())
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build()).then(Mono.fromRunnable(() -> {
            long duration = System.currentTimeMillis() - startTime;
            int statusCode = response.getStatusCode() != null ?
                    response.getStatusCode().value() : 0;
            log.info("请求完成 - IP: {}, Method: {}, Path: {}, Status: {}, Duration: {}ms",
                    clientIp, method, path, statusCode, duration);
        }));
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
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    @Override
    public int getOrder() {
        return 0; // 最后执行
    }
}