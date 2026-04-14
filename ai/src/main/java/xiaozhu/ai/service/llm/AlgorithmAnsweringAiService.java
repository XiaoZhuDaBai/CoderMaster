package xiaozhu.ai.service.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 算法题回答与详解 AI Service 接口
 *
 * 绑定系统提示词资源：prompt/algorithm-answering-prompt.txt
 */
public interface AlgorithmAnsweringAiService {
    @SystemMessage(fromResource = "prompt/algorithm-answering-prompt.txt")
    TokenStream answerAlgorithmStreaming(
            @V("language") String language,
            @V("output_format") String outputFormat,
            @UserMessage String question);
}


