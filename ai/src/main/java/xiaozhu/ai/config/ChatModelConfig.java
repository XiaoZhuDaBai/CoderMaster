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
import java.util.List;


/**
 * 用于生成题目/思考提示/判断结果
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/17 1:34
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.chat-model")
@Data
public class ChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;
    
    private Integer maxRetries;

    /**
     * 非流式模型 - 用于生成题目
     */
    @Bean
    @Scope("prototype")
    public ChatModel chatModelPrototype(List<ChatModelListener> chatModelListeners) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature)
                .logRequests(logRequests)
                .logResponses(logResponses)
                .listeners(chatModelListeners);

        if (maxRetries != null && maxRetries > 0) {
            builder.maxRetries(maxRetries);
        }
        
        // 禁用 thinking/reasoning 模式，避免 DeepSeek 返回 reasoning_content 导致的错误
        // langchain4j 1.14.0+ 支持 returnThinking() 方法
        builder.returnThinking(false);
        
        // DeepSeek API: 使用 reasoningEffort 参数控制思考模式
        // 设置为 "low" 或 "medium" 可以限制 thinking 长度，或设置 null 禁用
        builder.reasoningEffort(null);
        
        // DeepSeek 兼容: 通过 customParameters 传递 thinking 禁用
        // DeepSeek API 文档要求: thinking.type = "disabled" 禁用思考模式
        java.util.Map<String, Object> thinkingConfig = new java.util.HashMap<>();
        thinkingConfig.put("type", "disabled");
        java.util.Map<String, Object> customParams = new java.util.HashMap<>();
        customParams.put("thinking", thinkingConfig);
        builder.customParameters(customParams);
        
        return builder.build();
    }

}
