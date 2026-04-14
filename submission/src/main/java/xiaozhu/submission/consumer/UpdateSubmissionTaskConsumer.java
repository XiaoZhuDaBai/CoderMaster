package xiaozhu.submission.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import xiaozhu.common.constant.RabbitMQConstants;
import xiaozhu.common.message.JudgeResultMessage;
import xiaozhu.submission.service.SubmissionService;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/17 15:59
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UpdateSubmissionTaskConsumer {

    private final SubmissionService submissionService;

    @RabbitListener(queues = RabbitMQConstants.RESULT_QUEUE)
    public void handleJudgeResult(JudgeResultMessage resultMessage) {
        log.info("收到判题结果 submissionId={}, status={}",
                resultMessage.getSubmissionId(),
                resultMessage.getJudgeStatus());
        submissionService.applyJudgeResult(resultMessage);
    }
}
