package xiaozhu.ai.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
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
    public ChatLanguageModel chatModelPrototype(List<ChatModelListener> chatModelListeners) {
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
        return builder.build();
    }

}
