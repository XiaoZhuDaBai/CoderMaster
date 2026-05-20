package xiaozhu.ai.service.chat;


import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import xiaozhu.ai.service.common.ReasoningsParser;
import xiaozhu.ai.service.common.StreamingHandler;
import xiaozhu.ai.service.llm.AlgorithmAnsweringAiService;

import java.util.function.Consumer;

/**
 * 聊天问答服务实现类
 */
@Slf4j
@Service
public class ChatServiceImpl implements ChatService {

    private final AlgorithmAnsweringAiService normalStreamingService;
    private final AlgorithmAnsweringAiService reasoningStreamingService;

    public ChatServiceImpl(
            @Qualifier("streamingChatModelPrototype") StreamingChatModel streamingChatModel,
            @Qualifier("reasoningStreamingChatModelPrototype") StreamingChatModel reasoningStreamingChatModel) {
        this.normalStreamingService = AiServices.builder(AlgorithmAnsweringAiService.class)
                .streamingChatModel(streamingChatModel)
                .build();
        this.reasoningStreamingService = AiServices.builder(AlgorithmAnsweringAiService.class)
                .streamingChatModel(reasoningStreamingChatModel)
                .build();
    }

    @Override
    public void answerStreaming(String question, String language, String outputFormat, 
                               Consumer<String> onToken, boolean useReasoning) {
        if (language == null || language.isBlank()) {
            language = "Java,Python";
        }
        if (outputFormat == null || outputFormat.isBlank()) {
            outputFormat = "full";
        }

        AlgorithmAnsweringAiService service = useReasoning ? reasoningStreamingService : normalStreamingService;
        TokenStream tokenStream = service.answerAlgorithmStreaming(language, outputFormat, question);

        // 使用公共的流式处理工具
        StreamingHandler.processStreaming(
                tokenStream,
                onToken,
                useReasoning,
                useReasoning ? ReasoningsParser::parse : null
        );
    }
}
