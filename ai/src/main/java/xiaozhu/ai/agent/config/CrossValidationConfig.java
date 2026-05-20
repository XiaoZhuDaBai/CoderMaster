package xiaozhu.ai.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 交叉验证配置
 *
 * <p>控制交叉验证功能的启用/禁用，以及验证策略。</p>
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "ai.agent.cross-validation")
public class CrossValidationConfig {

    /**
     * 是否启用交叉验证
     * 默认 true（双 AI 交叉验证能有效检测 AI 对题目的语义理解偏差，是质量保障的核心环节）
     */
    private boolean enabled = true;

    /**
     * 交叉验证模型类型：
     * - "deepseek-chat"     : DeepSeek V3（非思考模型，速度快）
     * - "deepseek-reasoner" : DeepSeek R1（思考模型，理解更深）
     * - "qwen-plus"         : 通义千问 Plus
     * - "qwen-max"          : 通义千问 Max
     *
     * 默认使用与主模型不同的模型以最大化视角差异
     */
    private String modelType = "deepseek-chat";

    /**
     * 交叉验证的严格程度：
     * - "normal"  : 至少 50% 用例通过即可
     * - "strict"  : 必须 100% 用例通过
     * - "relaxed" : 至少 1 个用例通过即可
     */
    private String strictness = "normal";

    /**
     * 交叉验证何时触发：
     * - "always"        : 每次生成都触发
     * - "on_verify_fail": 仅在 verifySolutionCode 失败时触发
     * - "on_pre_verify" : 仅在预验证阶段触发
     */
    private String triggerMode = "always";

    /**
     * 独立生成的测试用例数量（每个用例都由交叉验证模型独立推导）
     * 默认 2 个
     */
    private int independentCaseCount = 2;

    /**
     * 预验证阶段是否使用交叉验证（buildVerifiedReferenceSamplesJson）
     */
    private boolean enableInPreVerification = true;

    /**
     * Agent 生成后是否使用交叉验证
     */
    private boolean enablePostValidation = false;

    /**
     * 连续多少次 verifySolutionCode 失败后触发交叉验证
     */
    private int triggerAfterConsecutiveFails = 1;

    /**
     * 是否强制要求交叉验证通过才允许进入下游
     * 注意：开启后，如果交叉验证模型与主模型产生分歧，可能导致所有生成都被阻止
     * 建议先关闭（默认 false），观察一段时间的分歧率后再决定
     */
    private boolean enforcePass = false;

    /**
     * 获取严格程度阈值
     */
    public double getPassThreshold() {
        return switch (strictness) {
            case "strict" -> 1.0;
            case "relaxed" -> 0.0;
            default -> 0.5;
        };
    }

    public boolean shouldTriggerAlways() {
        return "always".equalsIgnoreCase(triggerMode);
    }

    public boolean shouldTriggerOnVerifyFail() {
        return "on_verify_fail".equalsIgnoreCase(triggerMode)
                || "always".equalsIgnoreCase(triggerMode);
    }

    public boolean shouldTriggerOnPreVerify() {
        return "on_pre_verify".equalsIgnoreCase(triggerMode)
                || "always".equalsIgnoreCase(triggerMode);
    }
}
