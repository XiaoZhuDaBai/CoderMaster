package xiaozhu.ai.exception;

/**
 * AI 生成相关错误类型枚举
 */
public enum AiErrorType {

    // ========== AI 相关错误（可重试）==========
    AI_RESPONSE_EMPTY("AI 返回空响应", true),
    AI_JSON_EXTRACT_FAILED("无法从 AI 响应中提取 JSON", true),
    AI_JSON_PARSE_FAILED("JSON 解析失败", true),

    // ========== 沙箱相关错误（不可重试）==========
    SANDBOX_COMPILE_ERROR("沙箱编译错误", false),
    SANDBOX_RUNTIME_ERROR("沙箱运行时错误", false),
    SANDBOX_VERIFY_FAILED("沙箱验证失败（算法逻辑可能有误）", false),
    SANDBOX_TIMEOUT("沙箱执行超时", false),

    // ========== 业务相关错误（不可重试）==========
    SOLUTION_CODE_VALIDATION_FAILED("SolutionCode 格式验证失败", false),
    GENERATOR_CODE_VALIDATION_FAILED("GeneratorCode 格式验证失败", false),

    // ========== 通用错误 ==========
    UNKNOWN_ERROR("未知错误", true),
    AI_GENERATION_FAILED_AFTER_RETRY("AI 生成重试全部失败", false);

    private final String description;
    private final boolean retryable;  // 是否可重试

    AiErrorType(String description, boolean retryable) {
        this.description = description;
        this.retryable = retryable;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
