package xiaozhu.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/25 13:36
 */
@Configuration
public class ExecutorConfig implements AsyncConfigurer {
    
    @Bean(name = "testCaseGenerationExecutor")
    public ThreadPoolTaskExecutor testCaseGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 测试用例生成是耗时操作，适当提高并发度，避免多个题目排队等待过久
        executor.setCorePoolSize(3);
        executor.setMaxPoolSize(6);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("test-case-gen-");
        executor.initialize();
        return executor;
    }

    /**
     * 题目生成异步执行器
     */
    @Bean(name = "problemGenerationExecutor")
    public ThreadPoolTaskExecutor problemGenerationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("problem-gen-");
        executor.initialize();
        return executor;
    }
}
