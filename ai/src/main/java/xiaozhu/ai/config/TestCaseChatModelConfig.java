package xiaozhu.ai.config;

import dev.langchain4j.http.client.jdk.JdkHttpClient;
import dev.langchain4j.http.client.jdk.JdkHttpClientBuilder;
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
 * 测试用例生成专用配置（DeepSeek-V3.2 非思考模型）
 * 绑定 {@code langchain4j.open-ai.test-case-chat-model} 前缀下的配置
 *
 * 独立于现有的 {@link TestCaseReasoningChatModelConfig}，互不影响。
 * Bean 名称为 {@code testCaseChatModelV2Prototype}，通过配置开关切换使用。
 *
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/04/10
 */
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.test-case-chat-model")
@Data
public class TestCaseChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

    private Integer maxRetries;

    private Integer testCaseConnectTimeoutSeconds = 60;
    private Integer testCaseReadTimeoutSeconds = 240;
    private Integer testCaseRequestTimeoutSeconds = 300;

    /**
     * 非流式模型 - 专门用于生成测试用例，基于 DeepSeek-V3.2 非思考模型
     * 使用 deepseek-chat 代替 deepseek-reasoner，去除思考链，显著降低 output tokens
     */
    @Bean
    @Scope("prototype")
    public ChatLanguageModel testCaseChatModelV2Prototype(List<ChatModelListener> chatModelListeners) {
        Duration connectTimeout = Duration.ofSeconds(
                testCaseConnectTimeoutSeconds != null && testCaseConnectTimeoutSeconds > 0
                        ? testCaseConnectTimeoutSeconds
                        : 60);
        Duration readTimeout = Duration.ofSeconds(
                testCaseReadTimeoutSeconds != null && testCaseReadTimeoutSeconds > 0
                        ? testCaseReadTimeoutSeconds
                        : 240);
        Duration requestTimeout = Duration.ofSeconds(
                testCaseRequestTimeoutSeconds != null && testCaseRequestTimeoutSeconds > 0
                        ? testCaseRequestTimeoutSeconds
                        : 300);

        JdkHttpClientBuilder jdkHttpClientBuilder = JdkHttpClient.builder()
                .connectTimeout(connectTimeout)
                .readTimeout(readTimeout);

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .httpClientBuilder(jdkHttpClientBuilder)
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
        return builder.build();
    }
}
