package xiaozhu.ai.config;

import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import xiaozhu.ai.metrics.TokenUsageListener;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 流式对话模型配置
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.streaming-chat-model")
@Data
public class StreamingChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

    /**
     * 流式模型
     */
    @Bean
    @Scope("prototype")
    public StreamingChatModel streamingChatModelPrototype(List<ChatModelListener> chatModelListeners) {
        OpenAiStreamingChatModel.OpenAiStreamingChatModelBuilder builder = OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(chatModelListeners);

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
