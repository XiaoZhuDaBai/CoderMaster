package xiaozhu.ai.controller;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xiaozhu.ai.service.chat.ChatService;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Chat 接口，用于回答算法问题
 */
@Slf4j
@RestController
@RequestMapping("/api/chat/streaming")
public class ChatController {

    @Resource
    private ChatService chatService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 流式回答算法问题，使用 SSE 实时下发 token 片段（普通模式）
     */
    @PostMapping(value = "/answer", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answer(@RequestBody ChatRequest request) {
        return createStreamingResponse(request, false);
    }

    /**
     * 流式回答，使用 SSE 实时下发 token 片段（思考模式，适用于需要显示思考过程的场景）
     */
    @PostMapping(value = "/answer/thinking", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter answerThinking(@RequestBody ChatRequest request) {
        return createStreamingResponse(request, true);
    }

    /**
     * 创建流式SSE响应
     * @param request 请求对象
     * @param useReasoning 是否使用推理模式（带思考过程）
     */
    private SseEmitter createStreamingResponse(ChatRequest request, boolean useReasoning) {
        SseEmitter emitter = new SseEmitter(300_000L);
        if (request == null || request.getQuestion() == null || request.getQuestion().isBlank()) {
            try {
                emitter.send(SseEmitter.event().data("question 不能为空").name("error"));
            } catch (IOException ignored) {
            }
            emitter.complete();
            return emitter;
        }

        // 使用AtomicBoolean跟踪连接状态
        AtomicBoolean connectionActive = new AtomicBoolean(true);

        CompletableFuture.runAsync(() -> {
            try {
                chatService.answerStreaming(
                        request.getQuestion(),
                        request.getLanguage(),
                        request.getOutputFormat(),
                        token -> {
                            // 检查连接是否还活跃
                            if (!connectionActive.get()) {
                                return;
                            }
                            try {
                                if (Objects.nonNull(token) && !token.isBlank()) {
                                    emitter.send(SseEmitter.event().data(token).name("token"));
                                }
                            } catch (IOException e) {
                                log.warn("发送 SSE token 失败，连接可能已断开", e);
                                connectionActive.set(false);
                            }
                        },
                        useReasoning
                );
                // notify completion
                if (connectionActive.get()) {
                    try {
                        emitter.send(SseEmitter.event().data("[DONE]").name("complete"));
                    } catch (IOException ignore) {
                        log.warn("发送完成信号失败，连接可能已断开", ignore);
                    }
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("流式回答失败", e);
                if (connectionActive.get()) {
                    emitter.completeWithError(e);
                }
            }
        }, executorService);

        emitter.onCompletion(() -> {
            log.debug("SSE 连接已关闭");
            connectionActive.set(false);
        });
        emitter.onTimeout(() -> {
            log.warn("SSE 连接超时");
            connectionActive.set(false);
            emitter.complete();
        });
        emitter.onError((ex) -> {
            log.error("SSE 连接错误", ex);
            connectionActive.set(false);
        });

        return emitter;
    }

    @Setter
    @Getter
    public static class ChatRequest {
        private String question;
        private String language;
        private String outputFormat;
    }
}
