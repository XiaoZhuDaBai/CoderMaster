package xiaozhu.ai.config;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import xiaozhu.ai.metrics.TokenUsageListener;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 交叉验证专用模型配置
 *
 * <p>专门用于对 solutionCode 做语义层面的二次验证，使用与主 Agent 不同的模型，
 * 以打破"同一模型既生成又验证"的循环依赖。</p>
 *
 * <p>绑定 {@code langchain4j.open-ai.cross-validation-chat-model} 前缀下的配置。</p>
 *
 * <p>推荐使用与主模型不同的模型系列，例如：
 * <ul>
 *   <li>主模型用 deepseek-chat，交叉验证用 qwen-plus</li>
 *   <li>主模型用 deepseek-reasoner，交叉验证用 deepseek-chat（去掉思考链）</li>
 *   <li>主模型用 qwen-plus，交叉验证用 deepseek-chat</li>
 * </ul>
 * 不同模型在训练数据、架构设计上存在差异，能从不同角度审视题目，降低同时出错的概率。</p>
 */
@Slf4j
@Configuration
@ConfigurationProperties(prefix = "langchain4j.open-ai.cross-validation-chat-model")
@Data
public class CrossValidationChatModelConfig {

    private String baseUrl;

    private String apiKey;

    private String modelName;

    private Integer maxTokens;

    private Double temperature;

    private boolean logRequests;

    private boolean logResponses;

    private Integer maxRetries;

    /**
     * 交叉验证请求超时时间（秒）
     * 交叉验证模型需要独立阅读题目并生成用例，prompt 较长
     */
    private Integer requestTimeoutSeconds = 120;

    /**
     * 交叉验证专用模型
     *
     * <p>设计原则：
     * <ul>
     *   <li>prototype scope：每次注入都是新实例，避免状态污染</li>
     *   <li>独立于主 Agent 的 chatModelPrototype，保证不会共享对话历史</li>
     *   <li>temperature 适中（0.3~0.5）：既要有创造性（生成不同视角的测试用例），
     *       又要足够稳定（每次运行结果差异不要太大）</li>
     * </ul>
     */
    @Bean
    @Scope("prototype")
    public ChatModel crossValidationChatModelPrototype(List<ChatModelListener> chatModelListeners) {
        Duration requestTimeout = Duration.ofSeconds(
                requestTimeoutSeconds != null && requestTimeoutSeconds > 0
                        ? requestTimeoutSeconds
                        : 120);

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(modelName)
                .maxTokens(maxTokens)
                .temperature(temperature != null ? temperature : 0.3)
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

        ChatModel model = builder.build();
        log.info("[CrossValidationModel] 交叉验证模型初始化，modelName={}, baseUrl={}",
                modelName, baseUrl);
        return model;
    }
}
