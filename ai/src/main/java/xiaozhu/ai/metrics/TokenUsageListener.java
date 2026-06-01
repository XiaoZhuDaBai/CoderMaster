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
 * 并支持按模型分组统计指标。
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
    
    /**
     * 当前会话使用的模型名称
     */
    private static final ThreadLocal<String> SESSION_MODEL_NAME = ThreadLocal.withInitial(() -> "unknown");
    
    /**
     * 当前会话开始时间（用于计算响应时间）
     */
    private static final ThreadLocal<Long> SESSION_START_TIME = ThreadLocal.withInitial(() -> System.currentTimeMillis());

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
        SESSION_MODEL_NAME.remove();
        SESSION_START_TIME.set(System.currentTimeMillis());
    }

    /**
     * 设置当前会话的模型名称
     * @param modelName 模型名称
     */
    public static void setSessionModelName(String modelName) {
        if (modelName != null && !modelName.isBlank()) {
            SESSION_MODEL_NAME.set(modelName);
        }
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

    /**
     * 获取当前会话使用的模型名称
     */
    public static String getSessionModelName() {
        return SESSION_MODEL_NAME.get();
    }

    @Override
    public void onRequest(ChatModelRequestContext requestContext) {
        // 从请求参数中获取模型名称
        try {
            var parameters = requestContext.chatRequest().parameters();
            if (parameters != null && parameters.modelName() != null) {
                setSessionModelName(parameters.modelName());
                log.debug("[TokenUsageListener] 识别模型名称: {}", parameters.modelName());
            }
        } catch (Exception e) {
            log.trace("无法从请求参数获取模型名称: {}", e.getMessage());
        }
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

        // 尝试从响应元数据获取实际使用的模型名称（优先）
        try {
            if (metadata.modelName() != null) {
                setSessionModelName(metadata.modelName());
            }
        } catch (Exception e) {
            // 忽略
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

        // 计算响应时间
        long durationMs = System.currentTimeMillis() - SESSION_START_TIME.get();

        log.info("[TokenUsageListener] 捕获 Token 使用量 - input: {}, output: {}, total: {}, 耗时: {}ms",
                inputTokens, outputTokens, totalTokens, durationMs);

        // 记录到通用指标
        metricsService.recordTokenUsage(inputTokens, outputTokens);
        
        // 记录到模型分组指标
        String modelName = SESSION_MODEL_NAME.get();
        metricsService.recordModelTokenUsage(modelName, inputTokens, outputTokens);
        metricsService.recordModelCall(modelName, durationMs);
        
        // 重置开始时间，为下一次调用做准备
        SESSION_START_TIME.set(System.currentTimeMillis());
    }

    @Override
    public void onError(ChatModelErrorContext errorContext) {
        log.warn("[TokenUsageListener] AI 调用错误: {}", errorContext.error().getMessage());
        
        // 从错误上下文的请求中获取模型名称
        try {
            var parameters = errorContext.chatRequest().parameters();
            if (parameters != null && parameters.modelName() != null) {
                metricsService.recordModelError(parameters.modelName());
            }
        } catch (Exception e) {
            // 忽略
        }
    }
}
