package xiaozhu.problem.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import xiaozhu.common.constant.RabbitMQConstants;
import xiaozhu.common.message.ProblemGeneratedMessage;
import xiaozhu.problem.service.distribution.ProblemDeliveryService;
import xiaozhu.problem.service.distribution.ProblemPersistService;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProblemGenerationMessageConsumer {

    private final ProblemPersistService problemPersistService;
    private final ProblemDeliveryService problemDeliveryService;

    @RabbitListener(queues = RabbitMQConstants.PROBLEM_GENERATED_QUEUE)
    public void handleProblemGenerated(ProblemGeneratedMessage message) {
        if (message == null) {
            return;
        }
        log.info("=== 收到题目生成事件 === userKey={}, contentHash={}, occurredAt={}",
                message.getUserKey(), message.getContentHash(), message.getOccurredAt());
        if (!StringUtils.hasText(message.getUserKey()) || !StringUtils.hasText(message.getContentHash())) {
            log.warn("题目事件字段缺失，忽略");
            return;
        }

        long generatedAt = message.getOccurredAt() > 0 ? message.getOccurredAt() : System.currentTimeMillis();

        // 1. 先持久化题目到 MySQL（确保测试用例写入时能查到 questionId）
        log.info("=== 开始持久化题目 === userKey={}, contentHash={}", message.getUserKey(), message.getContentHash());
        Long questionId = problemPersistService.persistToMySQL(message.getUserKey(), message.getContentHash());
        log.info("=== 持久化结果 === questionId={}", questionId);
        if (questionId == null) {
            log.error("持久化题目失败，跳后续处理，userKey={}, contentHash={}",
                    message.getUserKey(), message.getContentHash());
            return;
        }

        // 2. 缓存题目到 Redis 传输区
        problemDeliveryService.cacheProblemFromSource(message.getUserKey(), message.getContentHash(), generatedAt);

        // 3. 持久化和缓存都完成后，删除源 key（释放空间）
        problemPersistService.deleteSourceKey(message.getUserKey(), message.getContentHash());
    }
}
