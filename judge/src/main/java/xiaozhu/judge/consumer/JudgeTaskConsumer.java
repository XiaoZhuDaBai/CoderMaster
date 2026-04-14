package xiaozhu.judge.consumer;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xiaozhu.common.constant.RabbitMQConstants;
import xiaozhu.common.dto.TestCaseDTO;
import xiaozhu.common.eenum.JudgeStatus;
import xiaozhu.common.eenum.JudgeTaskType;
import xiaozhu.common.feign.ProblemFeignClient;
import xiaozhu.common.message.JudgeResultMessage;
import xiaozhu.common.message.JudgeTaskMessage;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import xiaozhu.judge.codesandbox.CodeSandbox;
import xiaozhu.judge.model.ExecuteCodeRequest;
import xiaozhu.judge.model.ExecuteCodeResponse;
import xiaozhu.judge.model.JudgeInfo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/17 15:59
 */

@Slf4j
@Component
@RequiredArgsConstructor
public class JudgeTaskConsumer {

    private final CodeSandbox codeSandbox;
    private final RabbitTemplate rabbitTemplate;
    private final ProblemFeignClient problemFeignClient;

    @RabbitListener(queues = RabbitMQConstants.JUDGE_QUEUE)
    public void consume(JudgeTaskMessage taskMessage) {
        if (taskMessage == null) {
            return;
        }
        log.info("收到判题任务 submissionId={}, type={}",
                taskMessage.getSubmissionId(),
                taskMessage.getTaskType());
        try {
            ExecuteCodeRequest request = buildExecuteRequest(taskMessage);
            ExecuteCodeResponse response = JudgeTaskType.RUN_CASE.equals(taskMessage.getTaskType())
                    ? codeSandbox.userTestCode(request)
                    : codeSandbox.executeCode(request);
            sendResult(convertToResult(taskMessage, response));
        } catch (Exception e) {
            log.error("判题执行失败 submissionId={}, error={}", taskMessage.getSubmissionId(), e.getMessage(), e);
            sendResult(buildErrorResult(taskMessage, e.getMessage()));
        }
    }

    private ExecuteCodeRequest buildExecuteRequest(JudgeTaskMessage taskMessage) {
        ExecuteCodeRequest request = new ExecuteCodeRequest();
        request.setCode(taskMessage.getCode());
        request.setLanguage(taskMessage.getLanguage());
        if (taskMessage.getQuestionId() != null) {
            request.setProblemId(taskMessage.getQuestionId().toString());
        }
        request.setUserInput(taskMessage.getUserInput());
        
        // 如果是正式判题（非用户测试），通过 Feign 调用 problem-service 获取测试用例
        // 内部会自动处理：Redis 优先 → 未命中查 MySQL → 回写 Redis
        if (JudgeTaskType.SUBMISSION.equals(taskMessage.getTaskType()) 
                && StringUtils.hasText(taskMessage.getContentHash())) {
            List<TestCaseDTO> testCases = loadTestCases(taskMessage.getContentHash());
            request.setTestCases(testCases);
            log.info("加载测试用例，contentHash={}, 用例数量={}", 
                    taskMessage.getContentHash(), testCases.size());
        }
        
        return request;
    }

    /**
     * 通过 Feign 调用 problem-service 获取测试用例
     * 内部会自动处理：Redis 优先 → 未命中查 MySQL → 回写 Redis
     */
    private List<TestCaseDTO> loadTestCases(String contentHash) {
        try {
            List<TestCaseGenerationResponse.TestCaseDetail> details = 
                    problemFeignClient.getTestCasesByContentHash(contentHash);
            
            if (details == null || details.isEmpty()) {
                log.warn("测试用例为空，contentHash={}", contentHash);
                return Collections.emptyList();
            }

            return getTestCaseDTOS(details);
        } catch (Exception e) {
            log.error("调用 problem-service 获取测试用例失败，contentHash={}", contentHash, e);
            return Collections.emptyList();
        }
    }

    @NotNull
    private static List<TestCaseDTO> getTestCaseDTOS(List<TestCaseGenerationResponse.TestCaseDetail> details) {
        List<TestCaseDTO> testCases = new ArrayList<>();
        for (TestCaseGenerationResponse.TestCaseDetail detail : details) {
            TestCaseDTO dto = new TestCaseDTO();
            dto.setCaseIndex(detail.getCaseIndex());
            dto.setInput(detail.getInput());
            dto.setExpectedOutput(detail.getExpectedOutput());
            dto.setIsPublic(detail.getIsPublic());
            dto.setTimeLimit(detail.getTimeLimit());
            dto.setMemoryLimit(detail.getMemoryLimit());
            testCases.add(dto);
        }
        return testCases;
    }


    // 根据任务类型分别处理
    private JudgeResultMessage convertToResult(JudgeTaskMessage task, ExecuteCodeResponse response) {
        JudgeInfo judgeInfo = response.getJudgeInfo();

        // RUN_CASE（用户测试/自测）不进行对错判断，只返回运行结果
        if (JudgeTaskType.RUN_CASE.equals(task.getTaskType())) {
            return buildRunCaseResult(task, response, judgeInfo);
        }

        // SUBMISSION（正式提交）进行对错判断
        int totalCases = judgeInfo != null && judgeInfo.getCorrect() != null
                ? judgeInfo.getCorrect().length
                : 0;
        int passedCases = judgeInfo != null && judgeInfo.getCorrect() != null
                ? (int) IntStream.range(0, judgeInfo.getCorrect().length).filter(i -> judgeInfo.getCorrect()[i]).count()
                : 0;

        // 判断错误类型：编译错误(CE) 还是 运行时错误(RE)
        JudgeStatus errorStatus = detectErrorStatus(response, judgeInfo);

        JudgeStatus status;
        if (response.getExitCode() != null && response.getExitCode() != 0) {
            status = errorStatus != null ? errorStatus : JudgeStatus.RE;
        } else if (judgeInfo != null && judgeInfo.getErrorMessages() != null && !judgeInfo.getErrorMessages().isEmpty()) {
            status = errorStatus != null ? errorStatus : JudgeStatus.RE;
        } else if (totalCases > 0 && passedCases == totalCases) {
            status = JudgeStatus.AC;
        } else if (totalCases == 0) {
            status = JudgeStatus.AC;
        } else {
            status = JudgeStatus.WA;
        }

        return JudgeResultMessage.builder()
                .submissionId(task.getSubmissionId())
                .requestId(task.getRequestId())
                .judgeStatus(status)
                .totalCases(totalCases)
                .passedCases(passedCases)
                .timeCost(judgeInfo != null ? judgeInfo.getTime() : null)
                .memoryCost(judgeInfo != null ? judgeInfo.getMemory() : null)
                .errorMessage(judgeInfo != null && judgeInfo.getErrorMessages() != null ? String.join("\n", judgeInfo.getErrorMessages()) : null)
                .judgeResult(JSON.toJSONString(response))
                .outputList(response.getOutputList())
                .judgeTime(LocalDateTime.now())
                .build();
    }

    /**
     * 构建用户测试/运行样例的结果（不进行对错判断）
     */
    private JudgeResultMessage buildRunCaseResult(JudgeTaskMessage task, ExecuteCodeResponse response, JudgeInfo judgeInfo) {
        // 判断错误类型：编译错误(CE) 还是 运行时错误(RE)
        JudgeStatus errorStatus = detectErrorStatus(response, judgeInfo);

        JudgeStatus status;
        if (response.getExitCode() != null && response.getExitCode() != 0) {
            status = errorStatus != null ? errorStatus : JudgeStatus.RE;
        } else if (judgeInfo != null && judgeInfo.getErrorMessages() != null && !judgeInfo.getErrorMessages().isEmpty()) {
            status = errorStatus != null ? errorStatus : JudgeStatus.RE;
        } else {
            // 用户测试/运行样例：只要程序正常执行完成就认为是成功
            status = JudgeStatus.AC;
        }

        return JudgeResultMessage.builder()
                .submissionId(task.getSubmissionId())
                .requestId(task.getRequestId())
                .judgeStatus(status)
                .totalCases(0)
                .passedCases(0)
                .timeCost(judgeInfo != null ? judgeInfo.getTime() : null)
                .memoryCost(judgeInfo != null ? judgeInfo.getMemory() : null)
                .errorMessage(judgeInfo != null && judgeInfo.getErrorMessages() != null ? String.join("\n", judgeInfo.getErrorMessages()) : null)
                .judgeResult(JSON.toJSONString(response))
                .outputList(response.getOutputList())
                .judgeTime(LocalDateTime.now())
                .build();
    }

    /**
     * 检测错误类型：编译错误(CE) 或 运行时     * CodeSand错误(RE)
boxTemplate 已经在错误信息前面加了 [编译错误] 或 [运行时错误] 标记
     */
    private JudgeStatus detectErrorStatus(ExecuteCodeResponse response, JudgeInfo judgeInfo) {
        if (judgeInfo == null || judgeInfo.getErrorMessages() == null || judgeInfo.getErrorMessages().isEmpty()) {
            return null;
        }

        for (String errorMessage : judgeInfo.getErrorMessages()) {
            if (errorMessage != null) {
                String lowerMsg = errorMessage.toLowerCase();
                if (lowerMsg.contains("[编译错误]") || lowerMsg.contains("compile error") || lowerMsg.contains("compilation error")) {
                    return JudgeStatus.CE;
                }
                if (lowerMsg.contains("[运行时错误]") || lowerMsg.contains("runtime error")) {
                    return JudgeStatus.RE;
                }
            }
        }

        return null;
    }


    private JudgeResultMessage buildErrorResult(JudgeTaskMessage task, String errorMessage) {
        return JudgeResultMessage.builder()
                .submissionId(task.getSubmissionId())
                .requestId(task.getRequestId())
                .judgeStatus(JudgeStatus.SYSTEM_ERROR)
                .errorMessage(errorMessage)
                .judgeTime(LocalDateTime.now())
                .build();
    }

    private void sendResult(JudgeResultMessage result) {
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.RESULT_EXCHANGE,
                RabbitMQConstants.RESULT_ROUTING_KEY,
                result);
    }
}
