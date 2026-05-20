package xiaozhu.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 专门用于生成测试用例的思考模型配置（DeepSeek Reasoner 等）
 * 绑定 {@code langchain4j.open-ai.reasoning-chat-model} 前缀下的配置
 *
 * {@code testCaseChatModelPrototype}，以保证 {@link xiaozhu.ai.consumer.TestCasesGenerationMessageConsumer}
 * 等现有注入点无需改动。
 *
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/12/02
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.reasoning-chat-model")
@Data
public class TestCaseReasoningChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

    private Integer maxRetries;

    /**
     * 测试用例生成请求在 DeepSeek 上经常超过 60s，因此单独暴露超时配置
     */
    private Integer testCaseRequestTimeoutSeconds = 300;

    /**
     * 非流式模型 - 专门用于生成测试用例，基于思考模型（reasoning-chat-model）
     * 因为测试用例生成的 prompt 更长、响应体更大，需要更长的等待时间
     */
    @Bean
    @Scope("prototype")
    public ChatModel testCaseChatModelPrototype(List<ChatModelListener> chatModelListeners) {
        Duration requestTimeout = Duration.ofSeconds(
                testCaseRequestTimeoutSeconds != null && testCaseRequestTimeoutSeconds > 0
                        ? testCaseRequestTimeoutSeconds
                        : 300);

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .timeout(requestTimeout)
                .listeners(chatModelListeners);

        if (maxRetries != null && maxRetries > 0) {
            builder.maxRetries(maxRetries);
        }
        
        // 禁用 thinking/reasoning 模式，避免 DeepSeek 返回 reasoning_content 导致的错误
        builder.returnThinking(false);
        
        // DeepSeek API: 禁用 thinking 模式
        builder.reasoningEffort(null);
        
        // DeepSeek 兼容: 通过 customParameters 传递 thinking 禁用
        Map<String, Object> thinkingConfig = new HashMap<>();
        thinkingConfig.put("type", "disabled");
        Map<String, Object> customParams = new HashMap<>();
        customParams.put("thinking", thinkingConfig);
        builder.customParameters(customParams);
        
        return builder.build();
    }
}
