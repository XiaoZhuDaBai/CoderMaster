package xiaozhu.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * AI 服务指标收集器
 * 用于收集 AI 调用次数、token 花费等指标
 * 支持按模型分组统计
 */
@Slf4j
@Service
public class AiMetricsService {

    private final MeterRegistry meterRegistry;

    // 通用计数器
    private final Counter questionGenerationCounter;
    private final Counter testCaseGenerationCounter;
    private final Counter solutionGenerationCounter;
    private final Counter aiCallTotalCounter;
    private final Counter aiCallErrorCounter;

    // Token 计数器（通用）
    private final Counter promptTokensCounter;
    private final Counter completionTokensCounter;
    private final Counter totalTokensCounter;

    // 响应时间计时器（通用）
    private final Timer questionGenerationTimer;
    private final Timer testCaseGenerationTimer;
    private final Timer solutionGenerationTimer;

    // 按模型分组的指标缓存
    private final ConcurrentMap<String, ModelMetrics> modelMetricsCache = new ConcurrentHashMap<>();

    /**
     * 按模型分组的指标
     */
    public static class ModelMetrics {
        private final Counter promptTokens;
        private final Counter completionTokens;
        private final Counter totalTokens;
        private final Counter callCounter;
        private final Counter errorCounter;
        private final Timer durationTimer;

        public ModelMetrics(Counter promptTokens, Counter completionTokens, Counter totalTokens,
                           Counter callCounter, Counter errorCounter, Timer durationTimer) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.callCounter = callCounter;
            this.errorCounter = errorCounter;
            this.durationTimer = durationTimer;
        }

        public Counter getPromptTokens() { return promptTokens; }
        public Counter getCompletionTokens() { return completionTokens; }
        public Counter getTotalTokens() { return totalTokens; }
        public Counter getCallCounter() { return callCounter; }
        public Counter getErrorCounter() { return errorCounter; }
        public Timer getDurationTimer() { return durationTimer; }
    }

    public AiMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        // AI 调用次数计数器
        this.questionGenerationCounter = Counter.builder("ai.calls.total")
                .description("AI 题目生成调用次数")
                .tag("type", "question_generation")
                .register(meterRegistry);

        this.testCaseGenerationCounter = Counter.builder("ai.calls.total")
                .description("AI 测试用例生成调用次数")
                .tag("type", "testcase_generation")
                .register(meterRegistry);

        this.solutionGenerationCounter = Counter.builder("ai.calls.total")
                .description("AI 题解生成调用次数")
                .tag("type", "solution_generation")
                .register(meterRegistry);

        this.aiCallTotalCounter = Counter.builder("ai.calls.total")
                .description("AI 总调用次数")
                .tag("type", "all")
                .register(meterRegistry);

        this.aiCallErrorCounter = Counter.builder("ai.calls.errors")
                .description("AI 调用失败次数")
                .register(meterRegistry);

        // Token 花费计数器（通用）
        this.promptTokensCounter = Counter.builder("ai.tokens.total")
                .description("AI Prompt Token 总数")
                .tag("token_type", "prompt")
                .register(meterRegistry);

        this.completionTokensCounter = Counter.builder("ai.tokens.total")
                .description("AI Completion Token 总数")
                .tag("token_type", "completion")
                .register(meterRegistry);

        this.totalTokensCounter = Counter.builder("ai.tokens.total")
                .description("AI 总 Token 数")
                .tag("token_type", "total")
                .register(meterRegistry);

        // 响应时间计时器
        this.questionGenerationTimer = Timer.builder("ai.calls.duration")
                .description("AI 题目生成响应时间")
                .tag("type", "question_generation")
                .register(meterRegistry);

        this.testCaseGenerationTimer = Timer.builder("ai.calls.duration")
                .description("AI 测试用例生成响应时间")
                .tag("type", "testcase_generation")
                .register(meterRegistry);

        this.solutionGenerationTimer = Timer.builder("ai.calls.duration")
                .description("AI 题解生成响应时间")
                .tag("type", "solution_generation")
                .register(meterRegistry);
    }

    /**
     * 获取或创建指定模型的指标
     * @param modelName 模型名称
     * @return 模型的指标对象
     */
    public ModelMetrics getOrCreateModelMetrics(String modelName) {
        return modelMetricsCache.computeIfAbsent(modelName, this::createModelMetrics);
    }

    private ModelMetrics createModelMetrics(String modelName) {
        Counter promptTokens = Counter.builder("ai.model.tokens.total")
                .description("按模型分组的 Prompt Token 数")
                .tag("model", modelName)
                .tag("token_type", "prompt")
                .register(meterRegistry);

        Counter completionTokens = Counter.builder("ai.model.tokens.total")
                .description("按模型分组的 Completion Token 数")
                .tag("model", modelName)
                .tag("token_type", "completion")
                .register(meterRegistry);

        Counter totalTokens = Counter.builder("ai.model.tokens.total")
                .description("按模型分组的总 Token 数")
                .tag("model", modelName)
                .tag("token_type", "total")
                .register(meterRegistry);

        Counter callCounter = Counter.builder("ai.model.calls.total")
                .description("按模型分组的调用次数")
                .tag("model", modelName)
                .register(meterRegistry);

        Counter errorCounter = Counter.builder("ai.model.errors.total")
                .description("按模型分组的错误次数")
                .tag("model", modelName)
                .register(meterRegistry);

        Timer durationTimer = Timer.builder("ai.model.calls.duration")
                .description("按模型分组的响应时间")
                .tag("model", modelName)
                .register(meterRegistry);

        log.info("为模型 {} 创建指标监控", modelName);
        return new ModelMetrics(promptTokens, completionTokens, totalTokens, callCounter, errorCounter, durationTimer);
    }

    /**
     * 记录指定模型的 Token 使用量
     * @param modelName 模型名称
     * @param promptTokens Prompt Token 数量
     * @param completionTokens Completion Token 数量
     */
    public void recordModelTokenUsage(String modelName, long promptTokens, long completionTokens) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        ModelMetrics metrics = getOrCreateModelMetrics(modelName);
        if (promptTokens > 0) {
            metrics.getPromptTokens().increment(promptTokens);
        }
        if (completionTokens > 0) {
            metrics.getCompletionTokens().increment(completionTokens);
        }
        long total = promptTokens + completionTokens;
        if (total > 0) {
            metrics.getTotalTokens().increment(total);
        }
    }

    /**
     * 记录指定模型的调用
     * @param modelName 模型名称
     * @param durationMs 耗时（毫秒）
     */
    public void recordModelCall(String modelName, long durationMs) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        ModelMetrics metrics = getOrCreateModelMetrics(modelName);
        metrics.getCallCounter().increment();
        metrics.getDurationTimer().record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录指定模型的调用错误
     * @param modelName 模型名称
     */
    public void recordModelError(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return;
        }
        ModelMetrics metrics = getOrCreateModelMetrics(modelName);
        metrics.getErrorCounter().increment();
    }

    /**
     * 记录题目生成调用
     */
    public void recordQuestionGeneration(long durationMs) {
        questionGenerationCounter.increment();
        aiCallTotalCounter.increment();
        questionGenerationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录测试用例生成调用
     */
    public void recordTestCaseGeneration(long durationMs) {
        testCaseGenerationCounter.increment();
        aiCallTotalCounter.increment();
        testCaseGenerationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录题解生成调用
     */
    public void recordSolutionGeneration(long durationMs) {
        solutionGenerationCounter.increment();
        aiCallTotalCounter.increment();
        solutionGenerationTimer.record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * 记录 AI 调用错误
     */
    public void recordAiCallError() {
        aiCallErrorCounter.increment();
    }

    /**
     * 记录 AI 调用错误（带错误类型）
     *
     * @param errorType 错误类型，参见 AiErrorType 枚举
     */
    public void recordAiCallError(String errorType) {
        aiCallErrorCounter.increment();
        if (errorType != null) {
            Counter.builder("ai.calls.errors")
                    .description("AI 调用失败次数（按错误类型分类）")
                    .tag("error_type", errorType)
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * 记录 Token 使用量
     * @param promptTokens Prompt Token 数量
     * @param completionTokens Completion Token 数量
     */
    public void recordTokenUsage(long promptTokens, long completionTokens) {
        if (promptTokens > 0) {
            promptTokensCounter.increment(promptTokens);
        }
        if (completionTokens > 0) {
            completionTokensCounter.increment(completionTokens);
        }
        if (promptTokens > 0 || completionTokens > 0) {
            totalTokensCounter.increment(promptTokens + completionTokens);
        }
    }

    /**
     * 检查是否会超过单次生成的最大 token 预算。
     * 建议在 Agent 每次迭代开始前调用。
     *
     * @param maxBudget 允许的最大 token 数
     * @return 如果当前累计已超过预算则返回 true
     */
    public boolean isOverTokenBudget(long maxBudget) {
        // 暂不实现，由外部传入预算值
        return false;
    }
}

