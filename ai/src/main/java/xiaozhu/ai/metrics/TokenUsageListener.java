package xiaozhu.ai.metrics;

import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;

/**
 * LangChain4j ChatModelListener 实现，用于捕获所有 ChatLanguageModel 和
 * StreamingChatLanguageModel 调用完成后的真实 Token 使用量。
 *
 * <p>LangChain4j 通过 {@code ChatResponse.metadata().tokenUsage()} 获取
 * DeepSeek API 响应中返回的 usage 字段。
 *
 * <p>使用方式：在 OpenAiChatModel / OpenAiStreamingChatModel 的 builder 中
 * 通过 {@code .listeners(List.of(listener))} 注册。
 */
@Slf4j
public class TokenUsageListener implements ChatModelListener {

    private final AiMetricsService metricsService;

    public TokenUsageListener(AiMetricsService metricsService) {
        this.metricsService = metricsService;
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

        Long inputTokens = Long.valueOf(tokenUsage.inputTokenCount());
        Long outputTokens = Long.valueOf(tokenUsage.outputTokenCount());
        Long totalTokens = Long.valueOf(tokenUsage.totalTokenCount());

        log.info("[TokenUsageListener] 捕获 Token 使用量 - input: {}, output: {}, total: {}",
                inputTokens, outputTokens, totalTokens);

        metricsService.recordTokenUsage(
                inputTokens.intValue(),
                outputTokens.intValue()
        );
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("[TokenUsageListener] AI 调用错误: {}", errorContext.error().getMessage());
    }
}
