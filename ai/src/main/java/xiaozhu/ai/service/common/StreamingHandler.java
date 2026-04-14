package xiaozhu.ai.service.common;

import com.alibaba.fastjson2.JSON;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.TokenStream;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 流式处理公共工具类
 * 抽取 ChatService、SolutionService 中重复的流式处理逻辑
 */
@Slf4j
public class StreamingHandler {

    /**
     * 流式响应格式类
     */
    @Setter
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StreamingResponse {
        private String reasoningContent = "";
        private String content = "";

        public String toJsonString() {
            return JSON.toJSONString(this);
        }
    }

    /**
     * 处理流式响应
     *
     * @param tokenStream  LLM 返回的 TokenStream
     * @param onToken     每个 token 的回调函数
     * @param useReasoning 是否使用推理模式
     * @param parser      响应解析器（可选）
     * @return 完整内容
     */
    public static String processStreaming(
            TokenStream tokenStream,
            Consumer<String> onToken,
            boolean useReasoning,
            ResponseParser parser) {

        StringBuilder fullContentBuilder = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        // 用于跟踪上一次发送的状态
        StreamingResponse lastResponse = new StreamingResponse();

        tokenStream
                .onPartialResponse(partial -> {
                    if (partial == null) {
                        return;
                    }

                    // 累积完整内容
                    fullContentBuilder.append(partial);

                    try {
                        // 创建当前完整响应的对象
                        StreamingResponse currentResponse = new StreamingResponse();

                        // 根据是否使用推理模式来处理内容
                        if (useReasoning && parser != null) {
                            parser.parse(fullContentBuilder.toString(), currentResponse);
                        } else {
                            // 普通模式：所有内容都放在content中
                            currentResponse.setContent(fullContentBuilder.toString());
                        }

                        // 计算增量内容
                        StreamingResponse deltaResponse = calculateDelta(lastResponse, currentResponse);

                        // 发送增量
                        if (onToken != null) {
                            String jsonToken = deltaResponse.toJsonString();
                            onToken.accept(jsonToken);
                        }

                        // 更新状态
                        lastResponse.setReasoningContent(currentResponse.getReasoningContent());
                        lastResponse.setContent(currentResponse.getContent());
                    } catch (Exception e) {
                        log.warn("处理token时发生异常", e);
                        // 回退到发送纯文本增量
                        if (onToken != null) {
                            try {
                                onToken.accept(partial);
                            } catch (Exception ex) {
                                log.warn("onToken 回调异常", ex);
                            }
                        }
                    }
                })
                .onError(error -> {
                    errorRef.set(error);
                    log.error("流式处理过程中发生错误", error);
                    latch.countDown();
                })
                .onCompleteResponse((ChatResponse completeResponse) -> {
                    log.debug("流式处理完成");
                    latch.countDown();
                });

        tokenStream.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("流式处理被中断", e);
        }

        if (errorRef.get() != null) {
            throw new IllegalStateException("流式处理失败", errorRef.get());
        }

        return fullContentBuilder.toString();
    }

    /**
     * 计算增量响应
     */
    private static StreamingResponse calculateDelta(StreamingResponse last, StreamingResponse current) {
        StreamingResponse delta = new StreamingResponse();

        // 获取推理内容的增量
        if (current.getReasoningContent().length() > last.getReasoningContent().length()) {
            delta.setReasoningContent(
                current.getReasoningContent().substring(last.getReasoningContent().length())
            );
        }

        // 获取答案内容的增量
        if (current.getContent().length() > last.getContent().length()) {
            delta.setContent(
                current.getContent().substring(last.getContent().length())
            );
        }

        return delta;
    }

    /**
     * 响应解析器接口
     */
    public interface ResponseParser {
        void parse(String fullContent, StreamingResponse response);
    }
}
