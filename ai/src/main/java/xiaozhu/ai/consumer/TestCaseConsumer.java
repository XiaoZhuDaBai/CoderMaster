package xiaozhu.ai.consumer;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import xiaozhu.ai.exception.AiErrorType;
import xiaozhu.ai.exception.AiGenerationException;
import xiaozhu.ai.service.problem.TestCaseService;
import xiaozhu.common.constant.RabbitMQConstants;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import xiaozhu.common.message.ProblemGeneratedMessage;
import xiaozhu.common.message.TestCaseSyncMessage;

import java.util.concurrent.TimeUnit;

/**
 * 测试用例消息消费者
 * 异步消费题目生成事件，在本地生成测试用例并缓存到 Redis
 *
 * 不再支持降级模式（模式B存在AI幻觉问题），失败时通过重试机制保证可靠性
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestCaseConsumer {

    private static final int MAX_RETRIES;
    private static final long RETRY_DELAY_MS;
    private static final double RETRY_BACKOFF_MULTIPLIER;
    private static final long FAILED_STATUS_TTL_DAYS = 7;

    static {
        MAX_RETRIES = 3;
        RETRY_DELAY_MS = 2000;
        RETRY_BACKOFF_MULTIPLIER = 2.0;
    }

    private final RedisTemplate<String, Object> redisTemplate;
    private final TaskExecutor testCaseGenerationExecutor;
    private final TestCaseService testCaseService;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitMQConstants.PROBLEM_TESTCASE_GENERATED_QUEUE)
    public void onProblemGenerated(ProblemGeneratedMessage message) {
        if (message == null) {
            log.warn("[TestCaseConsumer] 收到空的题目生成消息，已忽略");
            return;
        }
        log.info("[TestCaseConsumer] 收到题目生成消息，redisKey={}, userKey={}, contentHash={}",
                message.getRedisKey(), message.getUserKey(), message.getContentHash());
        testCaseGenerationExecutor.execute(() -> handleMessageInternally(message));
    }

    private void handleMessageInternally(ProblemGeneratedMessage message) {
        String redisKey = message.getRedisKey();
        String userKey = message.getUserKey();
        String contentHash = message.getContentHash();
        long startTime = System.currentTimeMillis();

        if (redisKey == null || userKey == null || contentHash == null) {
            log.warn("[TestCaseConsumer] 题目生成消息缺少关键字段，message={}", message);
            return;
        }

        log.info("[TestCaseConsumer] 开始处理测试用例生成，userKey={}, contentHash={}", userKey, contentHash);

        try {
            // Step 1: 从 Redis 读取题目信息
            log.info("[TestCaseConsumer] 从 Redis 读取题目信息，redisKey={}", redisKey);
            ProblemGenerationResponse problem = (ProblemGenerationResponse) redisTemplate.opsForValue().get(redisKey);
            if (problem == null) {
                log.warn("[TestCaseConsumer] Redis 中未找到题目缓存，redisKey={}", redisKey);
                return;
            }
            log.info("[TestCaseConsumer] 题目信息读取成功，title={}", problem.getTitle());

            // Step 2: 检查测试用例是否已存在
            log.info("[TestCaseConsumer] 检查测试用例是否已存在，userKey={}, contentHash={}", userKey, contentHash);
            if (testCaseService.testCasesExist(userKey, contentHash)) {
                log.info("[TestCaseConsumer] 测试用例已存在，跳过生成。userKey={}, contentHash={}", userKey, contentHash);
                return;
            }
            log.info("[TestCaseConsumer] 测试用例不存在，开始生成...");

            // Step 3: 使用沙箱验算模式生成测试用例（方案A：expectedOutput 由代码执行产生，无幻觉）
            log.info("[TestCaseConsumer] 【沙箱验算模式】开始生成测试用例，userKey={}, contentHash={}", userKey, contentHash);
            TestCaseGenerationResponse response = null;
            AiGenerationException lastException = null;

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                try {
                    log.info("[TestCaseConsumer] 第 {}/{} 次尝试生成测试用例，题目={}", attempt, MAX_RETRIES, problem.getTitle());

                    response = testCaseService.generateVerifiedTestCases(problem);

                    if (response != null && response.getTestCases() != null && !response.getTestCases().isEmpty()) {
                        log.info("[TestCaseConsumer] 第 {} 次尝试成功！题目={}，用例数量={}",
                                attempt, problem.getTitle(), response.getTestCases().size());
                        break; // 成功，退出重试循环
                    }

                    // 返回了 response 但用例为空，视为失败
                    log.warn("[TestCaseConsumer] 第 {} 次尝试返回空结果，继续重试...", attempt);

                } catch (AiGenerationException e) {
                    lastException = e;
                    log.error("[TestCaseConsumer] 第 {} 次尝试发生业务异常: type={}, message={}",
                            attempt, e.getErrorType(), e.getMessage());

                    // 不可重试的错误类型，直接失败
                    if (!e.isRetryable()) {
                        log.warn("[TestCaseConsumer] 错误类型 {} 不可重试，放弃重试", e.getErrorType());
                        break;
                    }

                } catch (Exception e) {
                    lastException = new AiGenerationException(AiErrorType.UNKNOWN_ERROR, e.getMessage());
                    log.error("[TestCaseConsumer] 第 {} 次尝试发生未知异常: {}", attempt, e.getMessage(), e);
                }

                // 重试前等待（指数退避），最后一次不需要等待
                if (attempt < MAX_RETRIES) {
                    long delayMs = (long) (RETRY_DELAY_MS * Math.pow(RETRY_BACKOFF_MULTIPLIER, attempt - 1));
                    log.info("[TestCaseConsumer] 等待 {}ms 后进行第 {} 次尝试...", delayMs, attempt + 1);
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("[TestCaseConsumer] 重试等待被中断");
                        break;
                    }
                }
            }

            // 处理最终结果
            if (response == null || response.getTestCases() == null || response.getTestCases().isEmpty()) {
                // 全部重试失败
                handleGenerationFailure(message, problem, lastException);
                return;
            }

            // 统计用例类型
            long sampleCount = response.getTestCases().stream().filter(tc -> tc.getCaseType() != null && tc.getCaseType().equals("SAMPLE")).count();
            long hiddenCount = response.getTestCases().stream().filter(tc -> tc.getCaseType() != null && tc.getCaseType().equals("HIDDEN")).count();
            log.info("[TestCaseConsumer] 共生成 {} 个测试用例（SAMPLE: {}, HIDDEN: {}）", response.getTestCases().size(), sampleCount, hiddenCount);

            // Step 4: 保存到 Redis
            log.info("[TestCaseConsumer] 保存测试用例到 Redis...");
            testCaseService.saveTestCases(userKey, contentHash, response);
            log.info("[TestCaseConsumer] 成功缓存测试用例，userKey={}, contentHash={}", userKey, contentHash);

            // Step 5: 发送消息通知 problem-service 同步到 MySQL
            log.info("[TestCaseConsumer] 发送测试用例同步消息到 problem-service，contentHash={}", contentHash);
            TestCaseSyncMessage syncMessage = TestCaseSyncMessage.builder()
                    .contentHash(contentHash)
                    .userKey(userKey)
                    .testCases(response.getTestCases())
                    .build();
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.TESTCASE_SYNC_EXCHANGE,
                    RabbitMQConstants.TESTCASE_SYNC_ROUTING_KEY,
                    syncMessage);
            log.info("[TestCaseConsumer] 同步消息发送成功，contentHash={}", contentHash);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[TestCaseConsumer] 测试用例生成完成！总耗时: {}ms，userKey={}, contentHash={}, count={}",
                    duration, userKey, contentHash, response.getTestCases().size());

        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[TestCaseConsumer] 异步生成测试用例失败，耗时: {}ms，userKey={}, contentHash={}", duration, message.getUserKey(), message.getContentHash(), ex);
            handleGenerationFailure(message, null, new AiGenerationException(AiErrorType.UNKNOWN_ERROR, ex.getMessage()));
        }
    }

    /**
     * 处理测试用例生成失败
     */
    private void handleGenerationFailure(ProblemGeneratedMessage message,
                                         ProblemGenerationResponse problem,
                                         AiGenerationException exception) {
        String problemTitle = problem != null ? problem.getTitle() : "unknown";
        String errorType = exception != null ? exception.getErrorType().name() : "UNKNOWN";
        String errorMsg = exception != null ? exception.getMessage() : "未知错误";

        String failureKey = message.getRedisKey() + ":failed";
        String failureReason = String.format(
                "测试用例生成失败（已重试%d次）| 题目=%s | 错误类型=%s | 错误详情=%s",
                MAX_RETRIES,
                problemTitle,
                errorType,
                errorMsg
        );

        // 保存失败原因到 Redis，便于排查
        redisTemplate.opsForValue().set(failureKey, failureReason, FAILED_STATUS_TTL_DAYS, TimeUnit.DAYS);
        log.error("[TestCaseConsumer] {}", failureReason);

        // TODO: 发送告警通知（如钉钉、邮件）
        // notificationService.sendAlert("测试用例生成失败", failureReason);
    }
}
