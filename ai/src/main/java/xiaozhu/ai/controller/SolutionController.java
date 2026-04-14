package xiaozhu.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import xiaozhu.ai.model.ProblemGenerationRequest;
import xiaozhu.ai.model.SolutionGenerationRequest;
import xiaozhu.ai.service.solution.SolutionService;
import xiaozhu.common.annotation.RateLimit;
import xiaozhu.common.annotation.RequireLogin;
import xiaozhu.common.dto.ProblemGenerationResponse;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 题解生成接口
 */
@Slf4j
@RestController
@RequestMapping("/api/solution")
public class SolutionController {

    @Resource
    private SolutionService solutionService;

    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 流式生成题解（使用 Server-Sent Events，实时返回每个token）
     * 需要登录，限流：60秒内最多3次请求（题解生成较耗时，限制更严格）
     * 
     * 使用方式：
     * - 前端使用 EventSource 或 fetch API 接收流式数据
     * - 每个token会实时通过SSE发送给客户端
     * 
     * @param requestBody 包含题目生成请求参数和AI生成的题目内容
     * @return SseEmitter，用于流式传输
     */
    @PostMapping(value = "/generate/streaming", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @RequireLogin(message = "请先登录后再生成题解")
    @RateLimit(windowSize = 60, maxRequests = 5, message = "题解生成请求过于频繁，请稍后再试")
    public SseEmitter generateSolutionStreaming(
            @RequestBody SolutionGenerationRequest requestBody) {

        ProblemGenerationRequest request = requestBody.getRequest();
        ProblemGenerationResponse problem = requestBody.getProblem();
        
        // 用于收集流式生成的token，之后合并为完整输出用于日志记录
        final StringBuilder outputBuilder = new StringBuilder();

        SseEmitter emitter = new SseEmitter(300_000L); // 5分钟超时
        log.info("开始流式生成题解, request={}, problemSummary={}", request, problem == null ? "null" : problem);
        
        // 异步执行，避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                solutionService.generateSolutionStreaming(
                    request, 
                    problem, 
                    token -> {
                        try {
                            // 实时发送每个token给客户端
                            emitter.send(SseEmitter.event()
                                    .data(token)
                                    .name("token"));
                            // 记录每个token以便在服务端复现完整输出（用于调试和审计）
                            synchronized (outputBuilder) {
                                outputBuilder.append(token);
                            }
                            log.info("SSE token: {}", token);
                        } catch (IOException e) {
                            log.error("发送SSE数据失败", e);
                            emitter.completeWithError(e);
                        }
                    }
                );
                // 发送完成事件
                emitter.send(SseEmitter.event()
                        .data("[DONE]")
                        .name("complete"));

                // 在所有token发送完毕后，记录完整输出到日志（包含思考链与最终答案）
                String finalOutput;
                synchronized (outputBuilder) {
                    finalOutput = outputBuilder.toString();
                }
                if (!finalOutput.isEmpty()) {
                    log.info("题解流式生成完成，输出长度：{} 字符。\n{}", finalOutput.length(), finalOutput);
                } else {
                    log.warn("题解流式生成完成，但未收到任何token输出");
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("流式生成题解失败", e);
                emitter.completeWithError(e);
            }
        }, executorService);

        // 处理客户端断开连接
        emitter.onCompletion(() -> log.debug("SSE连接已关闭"));
        emitter.onTimeout(() -> {
            log.warn("SSE连接超时");
            emitter.complete();
        });
        emitter.onError((ex) -> log.error("SSE连接错误", ex));

        return emitter;
    }
}
