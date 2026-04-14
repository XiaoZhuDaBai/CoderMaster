package xiaozhu.common.filter;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xiaozhu.common.comm.HttpStatusEnum;
import xiaozhu.common.comm.ResponseResult;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * 网关来源验证过滤器
 * 用于验证请求是否来自网关，防止直接访问服务端口绕过网关
 * 
 * @author XiaoZhuDaBai
 * @version 1.0
 */
@Slf4j
@Component
@Order(1) // 优先级最高，在其他过滤器之前执行
@ConditionalOnProperty(name = "gateway.source-check.enabled", havingValue = "true", matchIfMissing = true)
public class GatewaySourceFilter implements Filter {

    @Value("${gateway.source-check.enabled:true}")
    private boolean enabled;

    @Value("${gateway.source-check.secret:}")
    private String secret;

    // 允许直接访问的路径（健康检查等）
    private static final List<String> ALLOWED_PATHS = Arrays.asList(
            "/actuator/health",
            "/actuator/info"
    );

    @Override
    public void doFilter(jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response, 
                         FilterChain chain) throws IOException, ServletException {
        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        String path = httpRequest.getRequestURI();

        // 检查是否在允许列表中
        if (isAllowedPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        // 检查请求头中是否有网关标识
        String gatewayHeader = httpRequest.getHeader("X-Gateway-Request");
        if (!"true".equals(gatewayHeader)) {
            log.warn("检测到直接访问服务，拒绝请求 - IP: {}, Path: {}", 
                    getClientIp(httpRequest), path);
            forbiddenResponse(httpResponse, "请求必须通过网关访问");
            return;
        }

        // 如果配置了密钥，验证密钥
        if (StringUtils.hasText(secret)) {
            String requestSecret = httpRequest.getHeader("X-Gateway-Secret");
            if (!secret.equals(requestSecret)) {
                log.warn("网关密钥验证失败 - IP: {}, Path: {}", 
                        getClientIp(httpRequest), path);
                forbiddenResponse(httpResponse, "网关密钥验证失败");
                return;
            }
        }

        chain.doFilter(request, response);
    }

    /**
     * 检查路径是否在允许列表中
     */
    private boolean isAllowedPath(String path) {
        return ALLOWED_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * 返回禁止访问响应
     */
    private void forbiddenResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json;charset=UTF-8");
        
        ResponseResult<Object> result = ResponseResult.fail(
                HttpStatusEnum.FORBIDDEN.getCode(), 
                message);
        String json = JSON.toJSONString(result);
        response.getWriter().write(json);
        response.getWriter().flush();
    }

    /**
     * 获取客户端 IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
}

