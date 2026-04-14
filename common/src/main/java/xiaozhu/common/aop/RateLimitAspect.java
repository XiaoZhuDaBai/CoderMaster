package xiaozhu.common.aop;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xiaozhu.common.annotation.RateLimit;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.common.util.RedisSlidingWindowRateLimiter;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * 限流切面
 * 基于用户ID的Redis滑动窗口限流
 * 放在common包中，供所有服务复用
 * 
 * @author XiaoZhuDaBai
 * @version 1.0
 */
@Slf4j
@Aspect
@Component
@Order(2) // 在登录验证之后执行
@RequiredArgsConstructor
@ConditionalOnBean(RedisSlidingWindowRateLimiter.class)
public class RateLimitAspect {

    private final RedisSlidingWindowRateLimiter rateLimiter;

    @Around(value = "@annotation(xiaozhu.common.annotation.RateLimit)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit == null) {
            return joinPoint.proceed();
        }

        // 获取请求对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("无法获取请求上下文，跳过限流检查");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        
        // 从请求头获取用户ID（网关会传递X-User-Id）
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            log.warn("请求头中未找到用户ID，跳过限流检查");
            return joinPoint.proceed();
        }

        // 构建限流键
        String rateLimitKey = rateLimit.keyPrefix() + ":" + userId;
        
        // 执行限流检查
        boolean allowed = rateLimiter.tryAcquire(
                rateLimitKey,
                rateLimit.windowSize(),
                rateLimit.maxRequests()
        );

        if (!allowed) {
            log.warn("用户请求被限流 - userId: {}, windowSize: {}s, maxRequests: {}", 
                    userId, rateLimit.windowSize(), rateLimit.maxRequests());
            
            // 返回限流响应
            HttpServletResponse response = attributes.getResponse();
            if (response != null) {
                returnRateLimitResponse(response, rateLimit.message(), rateLimit);
            }
            return null;
        }

        // 允许通过，继续执行
        return joinPoint.proceed();
    }

    /**
     * 返回限流响应
     */
    private void returnRateLimitResponse(HttpServletResponse response, String message, RateLimit rateLimit) {
        try {
            response.setStatus(429); // 429 Too Many Requests
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            // 添加限流响应头
            response.setHeader("X-RateLimit-Limit", String.valueOf(rateLimit.maxRequests()));
            response.setHeader("X-RateLimit-Window", String.valueOf(rateLimit.windowSize()));
            response.setHeader("Retry-After", String.valueOf(rateLimit.windowSize()));
            
            // 使用429状态码表示请求过多
            ResponseResult<Object> result = ResponseResult.fail(
                    429, 
                    message
            );
            
            String json = JSON.toJSONString(result);
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (IOException e) {
            log.error("返回限流响应失败", e);
        }
    }
}

