package xiaozhu.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 需要登录注解
 * 用于标记需要登录才能访问的方法
 * 
 * @author XiaoZhuDaBai
 * @version 1.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireLogin {
    
    /**
     * 未登录时的提示信息
     */
    String message() default "请先登录";
}

