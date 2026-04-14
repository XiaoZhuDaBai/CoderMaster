package xiaozhu.problem.service.distribution;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import xiaozhu.common.constant.RedisKeyConstant;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.problem.entity.Question;
import xiaozhu.problem.mapper.QuestionMapper;
import xiaozhu.problem.service.internal.CacheAsideTemplate;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemDeliveryServiceImpl implements ProblemDeliveryService {

    private static final long CACHE_TTL_DAYS = 7L;
    private static final long NEW_PROBLEM_THRESHOLD_HOURS = 24L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final QuestionMapper questionMapper;
    private final CacheAsideTemplate cacheAsideTemplate;

    private record ProblemWithTime(ProblemGenerationResponse response, long time) {}

    @Override
    public void cacheProblemFromSource(String userKey, String contentHash, long generatedAt) {
        if (!hasText(userKey) || !hasText(contentHash)) {
            log.warn("用户标识或内容哈希为空，跳过缓存 userKey={}, contentHash={}", userKey, contentHash);
            return;
        }
        String sourceKey = buildProblemKey(userKey, contentHash);
        Object cached = redisTemplate.opsForValue().get(sourceKey);
        if (cached == null) {
            log.warn("Redis 未找到题目数据，userKey={}, contentHash={}", userKey, contentHash);
            return;
        }
        ProblemGenerationResponse response = convertToProblem(cached);
        if (response == null) {
            log.warn("题目数据转换失败，userKey={}, contentHash={}", userKey, contentHash);
            return;
        }
        boolean isNew = isNewProblem(generatedAt);
        saveToDeliveryBucket(userKey, contentHash, response, isNew, generatedAt);
    }

    @Override
    public void cacheProblemFromSource(String userKey, String contentHash) {
        cacheProblemFromSource(userKey, contentHash, 0L);
    }

    @Override
    public List<ProblemGenerationResponse> listProblems(String userKey) {
        String deliveryKey = buildDeliveryKey(userKey);
        List<Object> values = redisTemplate.opsForHash().values(deliveryKey);
        if (CollectionUtils.isEmpty(values)) {
            loadFromIndex(userKey);
            values = redisTemplate.opsForHash().values(deliveryKey);
        }
        if (CollectionUtils.isEmpty(values)) {
            return Collections.emptyList();
        }
        return values.stream()
                .map(this::convertToProblem)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<ProblemGenerationResponse> listProblemsSorted(String userKey) {
        String deliveryKey = buildDeliveryKey(userKey);
        Map<Object, Object> problemEntries = redisTemplate.opsForHash().entries(deliveryKey);
        if (CollectionUtils.isEmpty(problemEntries)) {
            loadFromIndex(userKey);
            problemEntries = redisTemplate.opsForHash().entries(deliveryKey);
        }
        if (CollectionUtils.isEmpty(problemEntries)) {
            return Collections.emptyList();
        }

        List<String> hashStrings = problemEntries.keySet().stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();

        String timeKey = buildProblemTimeKey(userKey);
        List<Object> timeObjects = redisTemplate.opsForHash().multiGet(timeKey, (Collection<Object>) (Collection<?>) hashStrings);

        List<ProblemWithTime> problemsWithTime = new ArrayList<>();
        for (int i = 0; i < hashStrings.size(); i++) {
            String hash = hashStrings.get(i);
            Object problemObj = problemEntries.get(hash);
            Object timeObj = timeObjects.get(i);
            ProblemGenerationResponse resp = convertToProblem(problemObj);
            if (resp != null) {
                long time = parseTime(timeObj, hash);
                problemsWithTime.add(new ProblemWithTime(resp, time));
            }
        }

        problemsWithTime.sort(Comparator.comparingLong((ProblemWithTime p) -> p.time).reversed());
        return problemsWithTime.stream().map(p -> p.response).collect(Collectors.toList());
    }

    @Override
    public List<ProblemGenerationResponse> listNewProblems(String userKey) {
        String deliveryKey = buildNewDeliveryKey(userKey);
        Set<Object> hashKeys = redisTemplate.opsForHash().keys(deliveryKey);
        if (CollectionUtils.isEmpty(hashKeys)) {
            return Collections.emptyList();
        }

        List<String> hashStrings = hashKeys.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();

        if (hashStrings.isEmpty()) {
            return Collections.emptyList();
        }

        long now = Instant.now().toEpochMilli();
        long threshold = now - HOURS.toMillis(NEW_PROBLEM_THRESHOLD_HOURS);
        String timeKey = buildProblemTimeKey(userKey);

        List<Object> problemObjects = redisTemplate.opsForHash().multiGet(deliveryKey, (Collection<Object>) (Collection<?>) hashStrings);
        List<Object> timeObjects = redisTemplate.opsForHash().multiGet(timeKey, (Collection<Object>) (Collection<?>) hashStrings);

        List<ProblemGenerationResponse> result = new ArrayList<>();
        for (int i = 0; i < hashStrings.size(); i++) {
            String hash = hashStrings.get(i);
            Object problemObj = problemObjects.get(i);
            Object timeObj = timeObjects.get(i);
            if (timeObj == null) {
                continue;
            }
            long generatedAt = parseTime(timeObj, hash);
            if (generatedAt > 0 && generatedAt >= threshold) {
                ProblemGenerationResponse resp = convertToProblem(problemObj);
                if (resp != null) {
                    result.add(resp);
                }
            }
        }
        return result;
    }

    @Override
    public ProblemGenerationResponse getProblemByContentHash(String contentHash, String userKey) {
        if (!hasText(contentHash) || !hasText(userKey)) {
            return null;
        }
        String redisKey = RedisKeyConstant.QUESTION_PREFIX + userKey + ":" + contentHash;
        return cacheAsideTemplate.get(
            redisKey,
            () -> {
                Question question = questionMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                        .eq(Question::getContentHash, contentHash)
                );
                return question != null ? convertQuestionToResponse(question) : null;
            },
            ProblemGenerationResponse.class,
            CACHE_TTL_DAYS,
            DAYS
        );
    }

    private void loadFromIndex(String userKey) {
        String indexKey = RedisKeyConstant.QUESTION_USER_INDEX_PREFIX + userKey;
        Set<Object> hashes = redisTemplate.opsForSet().members(indexKey);
        if (CollectionUtils.isEmpty(hashes)) {
            return;
        }
        hashes.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .forEach(hash -> cacheProblemFromSource(userKey, hash));
    }

    private void saveToDeliveryBucket(String userKey, String contentHash,
            ProblemGenerationResponse response, boolean isNew, long generatedAt) {
        String totalDeliveryKey = buildDeliveryKey(userKey);
        redisTemplate.opsForHash().put(totalDeliveryKey, contentHash, response);
        redisTemplate.expire(totalDeliveryKey, CACHE_TTL_DAYS, DAYS);

        String deliveryKey = isNew ? buildNewDeliveryKey(userKey) : buildOldDeliveryKey(userKey);
        redisTemplate.opsForHash().put(deliveryKey, contentHash, response);
        redisTemplate.expire(deliveryKey, CACHE_TTL_DAYS, DAYS);

        if (generatedAt > 0) {
            String timeKey = buildProblemTimeKey(userKey);
            redisTemplate.opsForHash().put(timeKey, contentHash, generatedAt);
            redisTemplate.expire(timeKey, CACHE_TTL_DAYS, DAYS);
        }

        log.info("题目已写入传输区，userKey={}, contentHash={}, isNew={}, generatedAt={}",
                userKey, contentHash, isNew, generatedAt);
    }

    private boolean isNewProblem(long generatedAt) {
        if (generatedAt <= 0) {
            return false;
        }
        long now = Instant.now().toEpochMilli();
        long threshold = now - HOURS.toMillis(NEW_PROBLEM_THRESHOLD_HOURS);
        return generatedAt >= threshold;
    }

    private long parseTime(Object timeObj, String contentHash) {
        if (timeObj == null) {
            return 0L;
        }
        try {
            if (timeObj instanceof Long) {
                return (Long) timeObj;
            } else if (timeObj instanceof String) {
                return Long.parseLong((String) timeObj);
            } else {
                return Long.parseLong(timeObj.toString());
            }
        } catch (Exception e) {
            log.warn("解析题目生成时间失败，contentHash={}, timeObj={}, error={}",
                    contentHash, timeObj, e.getMessage());
            return 0L;
        }
    }

    private ProblemGenerationResponse convertToProblem(Object raw) {
        if (raw instanceof ProblemGenerationResponse response) {
            return response;
        }
        try {
            return JSON.parseObject(JSON.toJSONString(raw), ProblemGenerationResponse.class);
        } catch (Exception e) {
            log.error("题目数据反序列化失败", e);
            return null;
        }
    }

    private ProblemGenerationResponse convertQuestionToResponse(Question question) {
        ProblemGenerationResponse response = new ProblemGenerationResponse();
        response.setTitle(question.getTitle());
        response.setDescription(question.getDescription());
        response.setInputDesc(question.getInputDesc());
        response.setOutputDesc(question.getOutputDesc());
        response.setExamples(question.getExamples());
        response.setTimeLimit(question.getTimeLimit());
        response.setMemoryLimit(question.getMemoryLimit());
        response.setDifficulty(question.getDifficulty());
        return response;
    }

    private String buildProblemKey(String userKey, String contentHash) {
        return RedisKeyConstant.QUESTION_PREFIX + userKey + ":" + contentHash;
    }

    private String buildDeliveryKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + userKey;
    }

    private String buildNewDeliveryKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "new:" + userKey;
    }

    private String buildOldDeliveryKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "old:" + userKey;
    }

    private String buildProblemTimeKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "time:" + userKey;
    }

    private boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
