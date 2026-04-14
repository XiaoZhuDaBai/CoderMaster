package xiaozhu.ai.config;

import dev.langchain4j.model.chat.listener.ChatModelListener;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xiaozhu.ai.metrics.AiMetricsService;
import xiaozhu.ai.metrics.TokenUsageListener;

import java.util.List;

/**
 * AI 指标监听器配置。
 *
 * <p>将 {@link TokenUsageListener} 暴露为 Spring Bean，供所有
 * {@code OpenAiChatModel} / {@code OpenAiStreamingChatModel} 配置类注入使用。
 *
 * @see TokenUsageListener
 */
@Configuration
@RequiredArgsConstructor
public class TokenUsageConfig {

    private final AiMetricsService aiMetricsService;

    @Bean
    public TokenUsageListener tokenUsageListener() {
        return new TokenUsageListener(aiMetricsService);
    }

    @Bean
    public List<ChatModelListener> chatModelListeners(
            TokenUsageListener tokenUsageListener) {
        return List.of(tokenUsageListener);
    }
}
