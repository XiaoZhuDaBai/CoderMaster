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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class SubmissionServiceImpl implements SubmissionService {

    private static final String SUBMISSION_STATUS_PREFIX = "submission:status:";
    private static final Duration SUBMISSION_STATUS_TTL = Duration.ofMinutes(30);

    private final SubmissionMapper submissionMapper;

    private final RabbitTemplate rabbitTemplate;

    private final RedisTemplate<String, Object> redisTemplate;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmissionResponse createSubmission(SubmissionCreateRequest request) {
        Long questionId = resolveQuestionId(request.getQuestionId(), request.getContentHash(), request.getQuestionSnapshot());
        if (questionId == null) {
            throw new IllegalArgumentException("题目信息缺失，无法解析 questionId");
        }

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
        String requestId = buildRunCaseRequestId(request);

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

        // 幂等处理：检查是否为终态
        if (isTerminalStatus(resultMessage.getJudgeStatus())) {
            Submission current = submissionMapper.selectById(resultMessage.getSubmissionId());
            if (current != null && isTerminalStatus(current.getJudgeStatus())) {
                log.warn("重复判题结果，跳过更新，submissionId={}", resultMessage.getSubmissionId());
                return;
            }
        }

        // 1. 写 Redis（优先）
        writeSubmissionStatusToRedis(resultMessage);

        // 2. 写 MySQL
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

    private boolean isTerminalStatus(JudgeStatus status) {
        return status != null && (status == JudgeStatus.AC || status == JudgeStatus.WA
                || status == JudgeStatus.TLE || status == JudgeStatus.MLE
                || status == JudgeStatus.RE || status == JudgeStatus.CE
                || status == JudgeStatus.SYSTEM_ERROR);
    }

    @Override
    public SubmissionResponse getSubmission(Long submissionId) {
        // 1. 优先查 Redis
        SubmissionResponse cached = getSubmissionStatusFromRedis(submissionId);
        if (cached != null) {
            log.debug("从Redis获取提交结果，submissionId={}", submissionId);
            return cached;
        }

        // 2. Redis 未命中，查 MySQL
        Submission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return null;
        }

        // 3. 回写 Redis
        writeSubmissionStatusToRedisFromSubmission(submission);

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

    // ==================== Redis 缓存辅助方法 ====================

    private String buildSubmissionStatusKey(Long submissionId) {
        return SUBMISSION_STATUS_PREFIX + submissionId;
    }

    private void writeSubmissionStatusToRedis(JudgeResultMessage resultMessage) {
        try {
            String key = buildSubmissionStatusKey(resultMessage.getSubmissionId());
            Map<String, Object> status = new HashMap<>();
            if (resultMessage.getJudgeStatus() != null) {
                status.put("judgeStatus", resultMessage.getJudgeStatus().name());
            }
            if (resultMessage.getTotalCases() != null) {
                status.put("totalCases", resultMessage.getTotalCases());
            }
            if (resultMessage.getPassedCases() != null) {
                status.put("passedCases", resultMessage.getPassedCases());
            }
            if (resultMessage.getTimeCost() != null) {
                status.put("timeCost", resultMessage.getTimeCost());
            }
            if (resultMessage.getMemoryCost() != null) {
                status.put("memoryCost", resultMessage.getMemoryCost());
            }
            if (resultMessage.getErrorMessage() != null) {
                status.put("errorMessage", resultMessage.getErrorMessage());
            }
            if (resultMessage.getJudgeResult() != null) {
                status.put("judgeResult", resultMessage.getJudgeResult());
            }
            if (resultMessage.getOutputList() != null) {
                status.put("outputList", resultMessage.getOutputList());
            }
            // 统一使用时间戳存储
            status.put("judgeTime", resultMessage.getJudgeTime() == null
                    ? System.currentTimeMillis()
                    : resultMessage.getJudgeTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

            redisTemplate.opsForHash().putAll(key, status);
            redisTemplate.expire(key, SUBMISSION_STATUS_TTL);
            log.debug("判题结果已写入Redis，submissionId={}", resultMessage.getSubmissionId());
        } catch (Exception e) {
            log.warn("写Redis缓存失败，submissionId={}, error={}", resultMessage.getSubmissionId(), e.getMessage());
        }
    }

    private void writeSubmissionStatusToRedisFromSubmission(Submission submission) {
        try {
            String key = buildSubmissionStatusKey(submission.getSubmissionId());
            Map<String, Object> status = new HashMap<>();
            if (submission.getJudgeStatus() != null) {
                status.put("judgeStatus", submission.getJudgeStatus().name());
            }
            if (submission.getTotalCases() != null) {
                status.put("totalCases", submission.getTotalCases());
            }
            if (submission.getPassedCases() != null) {
                status.put("passedCases", submission.getPassedCases());
            }
            if (submission.getTimeCost() != null) {
                status.put("timeCost", submission.getTimeCost());
            }
            if (submission.getMemoryCost() != null) {
                status.put("memoryCost", submission.getMemoryCost());
            }
            if (submission.getErrorMessage() != null) {
                status.put("errorMessage", submission.getErrorMessage());
            }
            if (submission.getJudgeResult() != null) {
                status.put("judgeResult", submission.getJudgeResult());
            }
            if (submission.getJudgeTime() != null) {
                status.put("judgeTime", submission.getJudgeTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }
            if (submission.getUserId() != null) {
                status.put("userId", submission.getUserId());
            }
            if (submission.getQuestionId() != null) {
                status.put("questionId", submission.getQuestionId());
            }
            if (submission.getLanguage() != null) {
                status.put("language", submission.getLanguage());
            }

            redisTemplate.opsForHash().putAll(key, status);
            redisTemplate.expire(key, SUBMISSION_STATUS_TTL);
        } catch (Exception e) {
            log.warn("写Redis缓存失败，submissionId={}, error={}", submission.getSubmissionId(), e.getMessage());
        }
    }

    private SubmissionResponse getSubmissionStatusFromRedis(Long submissionId) {
        try {
            String key = buildSubmissionStatusKey(submissionId);
            Map<Object, Object> cached = redisTemplate.opsForHash().entries(key);
            if (cached.isEmpty()) {
                return null;
            }

            SubmissionResponse response = new SubmissionResponse();
            response.setSubmissionId(submissionId);

            Object userId = cached.get("userId");
            if (userId != null) {
                response.setUserId(Long.valueOf(userId.toString()));
            }

            Object questionId = cached.get("questionId");
            if (questionId != null) {
                response.setQuestionId(Long.valueOf(questionId.toString()));
            }

            Object language = cached.get("language");
            if (language != null) {
                response.setLanguage(language.toString());
            }

            Object judgeStatus = cached.get("judgeStatus");
            if (judgeStatus != null) {
                response.setJudgeStatus(JudgeStatus.valueOf(judgeStatus.toString()));
            }

            Object judgeResult = cached.get("judgeResult");
            if (judgeResult != null) {
                response.setJudgeResult(judgeResult.toString());
            }

            Object totalCases = cached.get("totalCases");
            if (totalCases != null) {
                response.setTotalCases(Integer.valueOf(totalCases.toString()));
            }

            Object passedCases = cached.get("passedCases");
            if (passedCases != null) {
                response.setPassedCases(Integer.valueOf(passedCases.toString()));
            }

            Object timeCost = cached.get("timeCost");
            if (timeCost != null) {
                response.setTimeCost(Long.valueOf(timeCost.toString()));
            }

            Object memoryCost = cached.get("memoryCost");
            if (memoryCost != null) {
                response.setMemoryCost(Long.valueOf(memoryCost.toString()));
            }

            Object errorMessage = cached.get("errorMessage");
            if (errorMessage != null) {
                response.setErrorMessage(errorMessage.toString());
            }

            Object outputList = cached.get("outputList");
            if (outputList instanceof List) {
                response.setOutputList((List<String>) outputList);
            }

            Object judgeTime = cached.get("judgeTime");
            if (judgeTime != null) {
                long ts;
                if (judgeTime instanceof Long) {
                    ts = (Long) judgeTime;
                } else {
                    ts = Long.parseLong(judgeTime.toString());
                }
                response.setJudgeTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()));
            }

            return response;
        } catch (Exception e) {
            log.warn("读Redis缓存失败，submissionId={}, error={}", submissionId, e.getMessage());
            return null;
        }
    }

    // ==================== 其他辅助方法 ====================

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
                submission.getUserId(),
                submission.getQuestionId(),
                submission.getLanguage(),
                submission.getJudgeStatus(),
                submission.getJudgeResult(),
                submission.getTotalCases(),
                submission.getPassedCases(),
                submission.getTimeCost(),
                submission.getMemoryCost(),
                submission.getErrorMessage(),
                null,
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
