package xiaozhu.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限流注解
 * 用于标记需要限流的方法
 * 
 * @author XiaoZhuDaBai
 * @version 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RateLimit {
    
    /**
     * 时间窗口大小（秒）
     * 默认60秒
     */
    int windowSize() default 60;
    
    /**
     * 时间窗口内允许的最大请求数
     * 默认10次
     */
    int maxRequests() default 10;
    
    /**
     * 限流键的前缀
     * 默认使用 "user"
     */
    String keyPrefix() default "user";
    
    /**
     * 限流提示信息
     */
    String message() default "请求过于频繁，请稍后再试";
}

