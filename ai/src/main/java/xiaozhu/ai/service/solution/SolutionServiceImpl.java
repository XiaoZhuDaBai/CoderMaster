package xiaozhu.ai.service.solution;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.TokenStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import xiaozhu.ai.metrics.AiMetricsService;
import xiaozhu.ai.model.ProblemGenerationRequest;
import xiaozhu.ai.model.SolutionGenerationContext;
import xiaozhu.ai.service.common.ReasoningsParser;
import xiaozhu.ai.service.common.StreamingHandler;
import xiaozhu.ai.service.llm.SolutionGenerationAiService;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.ProblemGenerationResponse.TestCase;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * 题解生成服务实现类
 */
@Slf4j
@Service
public class SolutionServiceImpl implements SolutionService {

    private final StreamingChatLanguageModel reasoningStreamingChatModel;
    private final SolutionGenerationAiService solutionGenerationAiService;

    public SolutionServiceImpl(
            @Qualifier("reasoningStreamingChatModelPrototype")
            StreamingChatLanguageModel reasoningStreamingChatModel) {
        this.reasoningStreamingChatModel = reasoningStreamingChatModel;
        this.solutionGenerationAiService = AiServices.builder(SolutionGenerationAiService.class)
                .streamingChatLanguageModel(reasoningStreamingChatModel)
                .build();
    }

    @Override
    public void generateSolutionStreaming(ProblemGenerationRequest request, 
                                       ProblemGenerationResponse problem, 
                                       Consumer<String> onToken) {
        if (request == null || problem == null) {
            throw new IllegalArgumentException("request 和 problem 均不能为空");
        }

        SolutionGenerationContext context = buildSolutionGenerationContext(request, problem);
        log.debug("题解Agent上下文：{}", context);

        invokeReasoningStreamingModel(context, onToken);
    }

    /**
     * 调用流式模型生成内容
     */
    private String invokeReasoningStreamingModel(SolutionGenerationContext context, Consumer<String> onToken) {
        if (reasoningStreamingChatModel == null) {
            throw new IllegalStateException("未找到推理流式模型配置");
        }

        StringBuilder contentBuilder = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        String instruction = "请基于以上题目参数和题面，按照系统消息中的结构，生成一份包含完整思考过程和最终答案的详细题解。";

        TokenStream tokenStream = solutionGenerationAiService.generateSolution(context, instruction);

        tokenStream
                .onPartialResponse(partial -> {
                    if (partial == null) {
                        return;
                    }
                    if (onToken != null) {
                        try {
                            onToken.accept(partial);
                        } catch (Exception e) {
                            log.warn("token回调处理异常", e);
                        }
                    }
                    contentBuilder.append(partial);
                })
                .onError(error -> {
                    errorRef.set(error);
                    log.error("题解流式生成过程中发生错误", error);
                    latch.countDown();
                })
                .onCompleteResponse((ChatResponse completeResponse) -> {
                    log.debug("题解流式生成完成");
                    latch.countDown();
                });

        tokenStream.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("题解生成被中断", e);
        }

        if (errorRef.get() != null) {
            throw new IllegalStateException("题解生成失败，请稍后重试", errorRef.get());
        }

        return contentBuilder.toString();
    }

    private SolutionGenerationContext buildSolutionGenerationContext(ProblemGenerationRequest request, ProblemGenerationResponse problem) {
        return SolutionGenerationContext.builder()
                .requestTags(formatList(request.getTagIds()))
                .requestDifficulty(defaultValue(request.getDifficulty(), "未知"))
                .requestSource(defaultValue(request.getSource(), "未指定"))
                .requestQuestionType(String.valueOf(Optional.ofNullable(request.getQuestionType()).orElse(0)))
                .requestTimeLimit(String.valueOf(Optional.ofNullable(request.getTimeLimit()).orElse(1000)))
                .requestMemoryLimit(String.valueOf(Optional.ofNullable(request.getMemoryLimit()).orElse(256)))
                .requestAdditionalRequirements(defaultValue(request.getAdditionalRequirements(), "无"))

                .problemTitle(defaultValue(problem.getTitle(), "未命名题目"))
                .problemDescription(defaultValue(problem.getDescription(), "无描述"))
                .problemInputSection(buildOptionalSection("### 输入描述", problem.getInputDesc()))
                .problemOutputSection(buildOptionalSection("### 输出描述", problem.getOutputDesc()))
                .problemExampleSection(buildOptionalSection("### 样例", problem.getExamples()))
                .problemTestCaseSection(buildTestCasesSection(problem.getTestCases()))

                .problemTags(formatList(problem.getTagNames()))
                .problemDifficulty(String.valueOf(Optional.ofNullable(problem.getDifficulty()).orElse(-1)))
                .problemTimeLimit(String.valueOf(Optional.ofNullable(problem.getTimeLimit()).orElse(request.getTimeLimit())))
                .problemMemoryLimit(String.valueOf(Optional.ofNullable(problem.getMemoryLimit()).orElse(request.getMemoryLimit())))
                .problemStackLimit(String.valueOf(Optional.ofNullable(problem.getStackLimit()).orElse(128)))
                .build();
    }

    private String buildOptionalSection(String title, String content) {
        if (StrUtil.isBlank(content)) {
            return "";
        }
        return title + "\n" + content + "\n\n";
    }

    private String buildTestCasesSection(List<TestCase> testCases) {
        if (CollUtil.isEmpty(testCases)) {
            return "";
        }
        String joined = testCases.stream()
                .map(this::formatTestCase)
                .collect(Collectors.joining("\n"));
        return "### 测试用例概览\n" + joined + "\n\n";
    }

    private String formatList(List<?> source) {
        if (CollUtil.isEmpty(source)) {
            return "[]";
        }
        return source.stream()
                .map(Object::toString)
                .collect(Collectors.joining(", ", "[", "]"));
    }

    private String defaultValue(String value, String defaultStr) {
        return StrUtil.isBlank(value) ? defaultStr : value;
    }

    private String formatTestCase(TestCase testCase) {
        return CharSequenceUtil.format(
                "- 用例#{}, 输入: {}, 期望输出: {}, 是否公开: {}",
                Optional.ofNullable(testCase.getCaseIndex()).orElse(0),
                defaultValue(testCase.getInput(), "未提供"),
                defaultValue(testCase.getExpectedOutput(), "未提供"),
                Optional.ofNullable(testCase.getIsPublic()).orElse(0) == 1 ? "公开" : "隐藏"
        );
    }
}
