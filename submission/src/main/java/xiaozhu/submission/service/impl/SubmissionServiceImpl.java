package xiaozhu.submission.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import xiaozhu.common.constant.RabbitMQConstants;
import xiaozhu.common.eenum.JudgeStatus;
import xiaozhu.common.eenum.JudgeTaskType;
import xiaozhu.common.message.JudgeResultMessage;
import xiaozhu.common.message.JudgeTaskMessage;
import xiaozhu.submission.mapper.SubmissionMapper;
import xiaozhu.submission.model.dto.RunCaseRequest;
import xiaozhu.submission.model.dto.RunCaseResultResponse;
import xiaozhu.submission.model.dto.SubmissionCreateRequest;
import xiaozhu.submission.model.dto.SubmissionResponse;
import xiaozhu.submission.model.entity.Submission;
import xiaozhu.submission.service.SubmissionService;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private final SubmissionMapper submissionMapper;

    private final RabbitTemplate rabbitTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmissionResponse createSubmission(SubmissionCreateRequest request) {
        // 解析题目 ID（支持数字 / 哈希 / 快照）
        Long questionId = resolveQuestionId(request.getQuestionId(), request.getContentHash(), request.getQuestionSnapshot());
        if (questionId == null) {
            throw new IllegalArgumentException("题目信息缺失，无法解析 questionId");
        }

        // 把请求对象变成提交对象
        Submission submission = getSubmission(request, questionId);

        submissionMapper.insert(submission);

        JudgeTaskMessage message = JudgeTaskMessage.builder()
                .submissionId(submission.getSubmissionId())
                .userId(request.getUserId())
                .questionId(questionId)
                .questionVersion(request.getQuestionVersion())
                .testSetVersion(request.getTestSetVersion())
                .code(request.getCode())
                .language(request.getLanguage())
                .questionSnapshot(request.getQuestionSnapshot())
                .contentHash(request.getContentHash())
                .taskType(JudgeTaskType.SUBMISSION)
                .build();
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.JUDGE_EXCHANGE,
                RabbitMQConstants.JUDGE_ROUTING_KEY,
                message);

        return buildSubmissionResponse(submission);
    }

    @Override
    public String forwardRunCase(RunCaseRequest request) {
        // 根据请求内容生成稳定的 requestId，用于命中缓存
        String requestId = buildRunCaseRequestId(request);

        // 先查 Redis，如果已有结果，直接返回相同 requestId，前端轮询会立即命中缓存，不再重复判题
        String cacheKey = buildRunCaseResultKey(requestId);
        Object cache = redisTemplate.opsForValue().get(cacheKey);
        if (cache instanceof RunCaseResultResponse) {
            return requestId;
        }

        Long questionId = resolveQuestionId(request.getQuestionId(), request.getContentHash(), request.getQuestionSnapshot());

        JudgeTaskMessage message = JudgeTaskMessage.builder()
                .submissionId(null)
                .userId(request.getUserId())
                .questionId(questionId)
                .code(request.getCode())
                .language(request.getLanguage())
                .userInput(request.getUserInput())
                .taskType(JudgeTaskType.RUN_CASE)
                .requestId(requestId)
                .build();
        rabbitTemplate.convertAndSend(
                RabbitMQConstants.JUDGE_EXCHANGE,
                RabbitMQConstants.JUDGE_ROUTING_KEY,
                message);
        return requestId;
    }

    @Override
    public void applyJudgeResult(JudgeResultMessage resultMessage) {
        if (resultMessage.getSubmissionId() == null) {
            // 运行案例结果，写入 Redis，TTL 24 小时
            RunCaseResultResponse response = new RunCaseResultResponse(
                    resultMessage.getRequestId(),
                    resultMessage.getJudgeStatus(),
                    resultMessage.getJudgeResult(),
                    resultMessage.getErrorMessage(),
                    resultMessage.getTimeCost(),
                    resultMessage.getMemoryCost()
            );
            String key = buildRunCaseResultKey(resultMessage.getRequestId());
            redisTemplate.opsForValue().set(key, response, Duration.ofHours(24));
            return;
        }
        LambdaUpdateWrapper<Submission> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(Submission::getSubmissionId, resultMessage.getSubmissionId())
                .set(resultMessage.getJudgeStatus() != null, Submission::getJudgeStatus, resultMessage.getJudgeStatus())
                .set(resultMessage.getTotalCases() != null, Submission::getTotalCases, resultMessage.getTotalCases())
                .set(resultMessage.getPassedCases() != null, Submission::getPassedCases, resultMessage.getPassedCases())
                .set(resultMessage.getTimeCost() != null, Submission::getTimeCost, resultMessage.getTimeCost())
                .set(resultMessage.getMemoryCost() != null, Submission::getMemoryCost, resultMessage.getMemoryCost())
                .set(resultMessage.getErrorMessage() != null, Submission::getErrorMessage, resultMessage.getErrorMessage())
                .set(resultMessage.getJudgeResult() != null, Submission::getJudgeResult, resultMessage.getJudgeResult())
                .set(Submission::getJudgeTime, resultMessage.getJudgeTime() == null ? LocalDateTime.now() : resultMessage.getJudgeTime());
        submissionMapper.update(null, wrapper);
    }

    @Override
    public SubmissionResponse getSubmission(Long submissionId) {
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return null;
        }
        return buildSubmissionResponse(submission);
    }

    @Override
    public RunCaseResultResponse getRunCaseResult(String requestId) {
        String key = buildRunCaseResultKey(requestId);
        Object cache = redisTemplate.opsForValue().get(key);
        if (cache instanceof RunCaseResultResponse) {
            return (RunCaseResultResponse) cache;
        }
        return null;
    }

    private Long resolveQuestionId(String questionIdentifier, String contentHash, String questionSnapshot) {
        String candidate = firstNonBlank(
                questionIdentifier,
                contentHash,
                extractFromSnapshot(questionSnapshot, "questionId"),
                extractFromSnapshot(questionSnapshot, "contentHash")
        );
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        try {
            return Long.valueOf(candidate);
        } catch (NumberFormatException ex) {
            return hashStringToLong(candidate);
        }
    }

    private String extractFromSnapshot(String snapshot, String fieldName) {
        if (!StringUtils.hasText(snapshot)) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(snapshot);
            JsonNode field = node.get(fieldName);
            return field != null && !field.isNull() ? field.asText() : null;
        } catch (Exception ex) {
            log.warn("解析题面快照失败 field={}", fieldName, ex);
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private long hashStringToLong(String value) {
        if (!StringUtils.hasText(value)) {
            return 0L;
        }
        byte[] digest = DigestUtils.md5Digest(value.getBytes(StandardCharsets.UTF_8));
        long result = 0L;
        for (int i = 0; i < Math.min(8, digest.length); i++) {
            result = (result << 8) | (digest[i] & 0xFF);
        }
        if (result == 0L) {
            result = value.hashCode();
        }
        return result & Long.MAX_VALUE;
    }

    private String buildRunCaseResultKey(String requestId) {
        return "run_case_result:" + requestId;
    }

    /**
     * 根据运行案例请求内容生成稳定的 requestId，便于相同请求命中缓存
     */
    private String buildRunCaseRequestId(RunCaseRequest request) {
        String questionKey = firstNonBlank(
                request.getQuestionId(),
                request.getContentHash(),
                extractFromSnapshot(request.getQuestionSnapshot(), "questionId"),
                extractFromSnapshot(request.getQuestionSnapshot(), "contentHash")
        );
        String raw = request.getUserId() + ":" +
                (questionKey == null ? "" : questionKey) + ":" +
                request.getLanguage() + ":" +
                request.getCode() + ":" +
                (request.getUserInput() == null ? "" : request.getUserInput());
        return DigestUtils.md5DigestAsHex(raw.getBytes(StandardCharsets.UTF_8));
    }

    private SubmissionResponse buildSubmissionResponse(Submission submission) {
        return new SubmissionResponse(
                submission.getSubmissionId(),
                submission.getQuestionId(),
                submission.getLanguage(),
                submission.getJudgeStatus(),
                submission.getJudgeResult(),
                submission.getTotalCases(),
                submission.getPassedCases(),
                submission.getTimeCost(),
                submission.getMemoryCost(),
                submission.getErrorMessage(),
                submission.getCreateTime(),
                submission.getJudgeTime()
        );
    }

    private static Submission getSubmission(SubmissionCreateRequest request, Long questionId) {
        Submission submission = new Submission();
        submission.setUserId(request.getUserId());
        submission.setQuestionId(questionId);
        submission.setCode(request.getCode());
        submission.setLanguage(request.getLanguage());
        submission.setJudgeStatus(JudgeStatus.PENDING);
        return submission;
    }
}

