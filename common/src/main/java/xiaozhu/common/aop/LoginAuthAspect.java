package xiaozhu.common.aop;

import com.alibaba.fastjson2.JSON;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import xiaozhu.common.annotation.RequireLogin;
import xiaozhu.common.comm.HttpStatusEnum;
import xiaozhu.common.comm.ResponseResult;

import java.io.IOException;
import java.lang.reflect.Method;

/**
 * 登录验证切面
 * 检查用户是否已登录（通过请求头中的X-User-Id判断）
 * 放在common包中，供所有服务复用
 * 
 * @author XiaoZhuDaBai
 * @version 1.0
 */
@Slf4j
@Aspect
@Component
@Order(1) // 优先级最高，在限流之前执行
@ConditionalOnClass(name = "jakarta.servlet.http.HttpServletRequest")
public class LoginAuthAspect {

    @Around("@annotation(xiaozhu.common.annotation.RequireLogin)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RequireLogin requireLogin = method.getAnnotation(RequireLogin.class);

        if (requireLogin == null) {
            return joinPoint.proceed();
        }

        // 获取请求对象
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            log.warn("无法获取请求上下文，跳过登录验证");
            return joinPoint.proceed();
        }

        HttpServletRequest request = attributes.getRequest();
        
        // 从请求头获取用户ID（网关认证通过后会传递X-User-Id）
        String userId = request.getHeader("X-User-Id");
        if (userId == null || userId.isEmpty()) {
            log.warn("请求头中未找到用户ID，用户未登录 - Path: {}", request.getRequestURI());
            
            // 返回未登录响应
            HttpServletResponse response = attributes.getResponse();
            if (response != null) {
                returnUnauthorizedResponse(response, requireLogin.message());
            }
            return null;
        }

        log.debug("用户已登录 - userId: {}, Path: {}", userId, request.getRequestURI());
        
        // 用户已登录，继续执行
        return joinPoint.proceed();
    }

    /**
     * 返回未授权响应
     */
    private void returnUnauthorizedResponse(HttpServletResponse response, String message) {
        try {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            
            ResponseResult<Object> result = ResponseResult.fail(
                    HttpStatusEnum.UNAUTHORIZED.getCode(), 
                    message
            );
            
            String json = JSON.toJSONString(result);
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (IOException e) {
            log.error("返回未授权响应失败", e);
        }
    }
}

