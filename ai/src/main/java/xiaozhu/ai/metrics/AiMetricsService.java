package xiaozhu.ai.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * AI 服务指标收集器
 * 用于收集 AI 调用次数、token 花费等指标
 */
@Slf4j
@Service
public class AiMetricsService {

    private final MeterRegistry meterRegistry;

    private final Counter questionGenerationCounter;
    private final Counter testCaseGenerationCounter;
    private final Counter solutionGenerationCounter;
    private final Counter aiCallTotalCounter;
    private final Counter aiCallErrorCounter;

    private final Counter promptTokensCounter;
    private final Counter completionTokensCounter;
    private final Counter totalTokensCounter;

    private final Timer questionGenerationTimer;
    private final Timer testCaseGenerationTimer;
    private final Timer solutionGenerationTimer;

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

        // Token 花费计数器
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
}

