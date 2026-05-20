package xiaozhu.ai.metrics;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.LongAdder;

/**
 * LangChain4j ChatModelListener 实现，用于捕获所有 ChatLanguageModel 和
 * StreamingChatLanguageModel 调用完成后的真实 Token 使用量。
 *
 * <p>LangChain4j 通过 {@code ChatResponse.metadata().tokenUsage()} 获取
 * DeepSeek API 响应中返回的 usage 字段。
 *
 * <p>使用方式：在 OpenAiChatModel / OpenAiStreamingChatModel 的 builder 中
 * 通过 {@code .listeners(List.of(listener))} 注册。
 *
 * <p>同时维护 ThreadLocal 级别的累计计数器，支持按生成会话追踪 token 消耗。
 */
@Slf4j
public class TokenUsageListener implements ChatModelListener {

    /**
     * 每个线程（生成会话）累计的 token 数。
     * 通过 {@link #resetSession()} 开始新会话，通过 {@link #getSessionTotalTokens()} 查询。
     */
    private static final ThreadLocal<LongAdder> SESSION_TOTAL_TOKENS = ThreadLocal.withInitial(LongAdder::new);
    private static final ThreadLocal<LongAdder> SESSION_INPUT_TOKENS = ThreadLocal.withInitial(LongAdder::new);
    private static final ThreadLocal<LongAdder> SESSION_OUTPUT_TOKENS = ThreadLocal.withInitial(LongAdder::new);

    private final AiMetricsService metricsService;

    public TokenUsageListener(AiMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    /**
     * 开始一个新的生成会话计数。
     * 必须在每次 {@code TestCaseGenerationAgentService.generate()} 调用前调用。
     */
    public static void resetSession() {
        SESSION_TOTAL_TOKENS.get().reset();
        SESSION_INPUT_TOKENS.get().reset();
        SESSION_OUTPUT_TOKENS.get().reset();
    }

    /**
     * 获取当前会话累计的 total tokens。
     */
    public static long getSessionTotalTokens() {
        return SESSION_TOTAL_TOKENS.get().sum();
    }

    /**
     * 获取当前会话累计的 input tokens。
     */
    public static long getSessionInputTokens() {
        return SESSION_INPUT_TOKENS.get().sum();
    }

    /**
     * 获取当前会话累计的 output tokens。
     */
    public static long getSessionOutputTokens() {
        return SESSION_OUTPUT_TOKENS.get().sum();
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 无需处理
    }

    @Override
    public void onResponse(ChatModelResponseContext responseContext) {
        var chatResponse = responseContext.chatResponse();
        if (chatResponse == null) {
            log.debug("[TokenUsageListener] chatResponse 为 null");
            return;
        }

        ChatResponseMetadata metadata = chatResponse.metadata();
        if (metadata == null) {
            log.debug("[TokenUsageListener] ChatResponseMetadata 为 null");
            return;
        }

        TokenUsage tokenUsage = metadata.tokenUsage();
        if (tokenUsage == null) {
            log.debug("[TokenUsageListener] 未能获取 TokenUsage，可能 API 未返回 usage 字段");
            return;
        }

        int inputTokens = tokenUsage.inputTokenCount();
        int outputTokens = tokenUsage.outputTokenCount();
        int totalTokens = tokenUsage.totalTokenCount();

        // 累加到当前会话
        SESSION_TOTAL_TOKENS.get().add(totalTokens);
        SESSION_INPUT_TOKENS.get().add(inputTokens);
        SESSION_OUTPUT_TOKENS.get().add(outputTokens);

        log.info("[TokenUsageListener] 捕获 Token 使用量 - input: {}, output: {}, total: {} (会话累计: {}/{})",
                inputTokens, outputTokens, totalTokens,
                SESSION_TOTAL_TOKENS.get().sum(), SESSION_INPUT_TOKENS.get().sum() + SESSION_OUTPUT_TOKENS.get().sum());

        metricsService.recordTokenUsage(inputTokens, outputTokens);
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("[TokenUsageListener] AI 调用错误: {}", errorContext.error().getMessage());
    }
}
