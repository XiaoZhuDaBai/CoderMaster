package xiaozhu.ai.service.chat;

import java.util.function.Consumer;

/**
 * 聊天问答服务接口
 */
public interface ChatService {

    /**
     * 流式回答算法问题
     *
     * @param question    问题内容
     * @param language   编程语言
     * @param outputFormat 输出格式
     * @param onToken   每个token的回调函数
     * @param useReasoning 是否使用推理模式（带思考过程）
     */
    void answerStreaming(String question, String language, String outputFormat, 
                        Consumer<String> onToken, boolean useReasoning);
}
