package xiaozhu.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import xiaozhu.common.util.RedisSlidingWindowRateLimiter;

/**
 * Redis 配置类
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/17
 */
@Configuration
public class RedisConfig {

    /**
     * 配置 RedisTemplate<String, Object>
     * 用于存储和读取对象类型的数据
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 设置 key 的序列化器为 StringRedisSerializer
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());

        // 设置 value 的序列化器为 GenericJackson2JsonRedisSerializer（支持对象序列化）
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        // 初始化 RedisTemplate
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public RedisSlidingWindowRateLimiter redisSlidingWindowRateLimiter(RedisTemplate<String, Object> redisTemplate) {
        return new RedisSlidingWindowRateLimiter(redisTemplate);
    }
}

