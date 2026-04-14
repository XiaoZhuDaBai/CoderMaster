package xiaozhu.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xiaozhu.common.util.JwtUtil;

/**
 * JWT配置类
 * 将 JwtUtil 配置为 Spring Bean，从配置文件中读取参数
 * 
 * @author XiaoZhuDaBai
 * @date 2025/10/10
 */
@Configuration
public class JwtConfig {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expire-time}")
    private long expireTime;

    /**
     * 创建 JwtUtil Bean
     * 
     * @return JwtUtil 实例
     */
    @Bean
    public JwtUtil jwtUtil() {
        return new JwtUtil(secret, expireTime);
    }
}

