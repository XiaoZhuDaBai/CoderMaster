package xiaozhu.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Agent 配置
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.agent")
public class AgentConfig {

    /**
     * 最大迭代次数
     */
    private int maxIterations = 15;

    /**
     * 单次生成最大 token 预算（含 input + output）。
     * 超过此预算时 Agent 立即停止迭代，防止 token 无限膨胀。
     * 0 表示不限制。
     */
    private long maxTokenBudgetPerGeneration = 150000;

    /**
     * 连续验证失败多少次后提前退出（避免无意义的继续迭代）
     */
    private int maxConsecutiveVerifyFailures = 2;

    /**
     * 是否启用 Agent 模式
     */
    private boolean enabled = false;

    /**
     * Agent 模式：REACT（默认）、PLANNER
     */
    private String mode = "REACT";

    /**
     * 短期记忆 TTL（秒）
     */
    private int shortTermMemoryTtlSeconds = 3600;

    /**
     * 是否记录成功案例
     */
    private boolean recordSuccessCases = true;

    /**
     * 是否记录失败案例
     */
    private boolean recordFailureCases = true;

    /**
     * 评审阈值（分数 >= 此值时通过）
     */
    private int reviewPassThreshold = 60;
}
