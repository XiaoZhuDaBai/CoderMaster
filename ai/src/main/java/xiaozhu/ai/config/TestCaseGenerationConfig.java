package xiaozhu.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 测试用例生成配置
 *
 * <p>绑定 ai.testcase.generation.* 配置项。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.testcase.generation")
public class TestCaseGenerationConfig {

    /**
     * 最大重试次数
     */
    private int maxRetries = 3;

    /**
     * 首次重试延迟（毫秒）
     */
    private long retryDelayMs = 2000;

    /**
     * 重试间隔退避倍数（指数退避）
     * 例如 2.0 表示：第1次重试延迟 2000ms，第2次 4000ms，第3次 8000ms
     */
    private double retryBackoffMultiplier = 2.0;

    /**
     * 计算第 N 次重试的延迟（毫秒）
     *
     * @param attempt 当前重试次数（从 1 开始）
     * @return 等待时间（毫秒）
     */
    public long calculateDelayMs(int attempt) {
        if (attempt <= 1) {
            return retryDelayMs;
        }
        return (long) (retryDelayMs * Math.pow(retryBackoffMultiplier, attempt - 1));
    }
}
