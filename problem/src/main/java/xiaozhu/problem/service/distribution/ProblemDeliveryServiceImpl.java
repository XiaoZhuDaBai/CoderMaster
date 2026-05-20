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
    private static final long DETAIL_TTL_DAYS = 30L;
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
        // 1. 从索引获取 contentHash 列表
        Set<Object> hashes = redisTemplate.opsForSet().members(buildIndexKey(userKey));
        if (CollectionUtils.isEmpty(hashes)) {
            loadFromIndex(userKey);
            hashes = redisTemplate.opsForSet().members(buildIndexKey(userKey));
        }
        if (CollectionUtils.isEmpty(hashes)) {
            return Collections.emptyList();
        }

        // 2. 按需读取每个题目的详情
        List<ProblemGenerationResponse> result = new ArrayList<>();
        for (Object hashObj : hashes) {
            String hash = hashObj.toString();
            ProblemGenerationResponse problem = getProblemDetail(hash);
            if (problem != null) {
                result.add(problem);
            }
        }
        return result;
    }

    @Override
    public List<ProblemGenerationResponse> listProblemsSorted(String userKey) {
        // 1. 从索引获取 contentHash 列表
        Set<Object> hashes = redisTemplate.opsForSet().members(buildIndexKey(userKey));
        if (CollectionUtils.isEmpty(hashes)) {
            loadFromIndex(userKey);
            hashes = redisTemplate.opsForSet().members(buildIndexKey(userKey));
        }
        if (CollectionUtils.isEmpty(hashes)) {
            return Collections.emptyList();
        }

        List<String> hashList = hashes.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();

        // 2. 获取时间戳
        String timeKey = buildTimeKey(userKey);
        List<Object> timeObjects = redisTemplate.opsForHash().multiGet(timeKey, new ArrayList<>(hashList));

        // 3. 按需读取每个题目的详情
        List<ProblemWithTime> problemsWithTime = new ArrayList<>();
        for (int i = 0; i < hashList.size(); i++) {
            String hash = hashList.get(i);
            long time = parseTime(timeObjects.get(i), hash);
            ProblemGenerationResponse resp = getProblemDetail(hash);
            if (resp != null) {
                problemsWithTime.add(new ProblemWithTime(resp, time));
            }
        }

        // 4. 按时间排序
        problemsWithTime.sort(Comparator.comparingLong((ProblemWithTime p) -> p.time).reversed());
        return problemsWithTime.stream().map(p -> p.response).collect(Collectors.toList());
    }

    @Override
    public List<ProblemGenerationResponse> listNewProblems(String userKey) {
        String newKey = buildNewKey(userKey);
        Set<Object> hashKeys = redisTemplate.opsForSet().members(newKey);
        if (CollectionUtils.isEmpty(hashKeys)) {
            return Collections.emptyList();
        }

        List<String> hashList = hashKeys.stream()
                .filter(Objects::nonNull)
                .map(Object::toString)
                .toList();

        long now = Instant.now().toEpochMilli();
        long threshold = now - HOURS.toMillis(NEW_PROBLEM_THRESHOLD_HOURS);
        String timeKey = buildTimeKey(userKey);

        List<Object> timeObjects = redisTemplate.opsForHash().multiGet(timeKey, new ArrayList<>(hashList));

        List<ProblemGenerationResponse> result = new ArrayList<>();
        for (int i = 0; i < hashList.size(); i++) {
            String hash = hashList.get(i);
            Object timeObj = timeObjects.get(i);
            if (timeObj == null) {
                continue;
            }
            long generatedAt = parseTime(timeObj, hash);
            if (generatedAt > 0 && generatedAt >= threshold) {
                ProblemGenerationResponse resp = getProblemDetail(hash);
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

    /**
     * 按需获取题目详情，Key 失效时自动回源到 MySQL
     */
    private ProblemGenerationResponse getProblemDetail(String contentHash) {
        String detailKey = buildDetailKey(contentHash);
        Object cached = redisTemplate.opsForValue().get(detailKey);
        if (cached != null) {
            return convertToProblem(cached);
        }

        // Key 失效，回源到 MySQL
        log.info("题目详情 Key 失效，开始回源，contentHash={}", contentHash);
        Question question = questionMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getContentHash, contentHash)
        );
        if (question == null) {
            log.warn("MySQL 中也不存在该题目，contentHash={}", contentHash);
            return null;
        }

        ProblemGenerationResponse response = convertQuestionToResponse(question);

        // 回写 Redis，详情独立 TTL
        redisTemplate.opsForValue().set(detailKey, response, DETAIL_TTL_DAYS, DAYS);
        log.info("题目详情已回写 Redis，contentHash={}", contentHash);

        return response;
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
        // 1. 写入索引 Set
        String indexKey = buildIndexKey(userKey);
        redisTemplate.opsForSet().add(indexKey, contentHash);

        // 2. 写入详情 Key（各自独立 TTL）
        String detailKey = buildDetailKey(contentHash);
        redisTemplate.opsForValue().set(detailKey, response, DETAIL_TTL_DAYS, DAYS);

        // 3. 写入时间戳 Hash
        if (generatedAt > 0) {
            String timeKey = buildTimeKey(userKey);
            redisTemplate.opsForHash().put(timeKey, contentHash, generatedAt);
            redisTemplate.expire(timeKey, CACHE_TTL_DAYS, DAYS);
        }

        // 4. 新题标识 Set
        if (isNew) {
            String newKey = buildNewKey(userKey);
            redisTemplate.opsForSet().add(newKey, contentHash);
            redisTemplate.expire(newKey, NEW_PROBLEM_THRESHOLD_HOURS, HOURS);
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

    // ==================== Key 构建方法 ====================

    private String buildProblemKey(String userKey, String contentHash) {
        return RedisKeyConstant.QUESTION_PREFIX + userKey + ":" + contentHash;
    }

    private String buildIndexKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "index:" + userKey;
    }

    private String buildDetailKey(String contentHash) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "detail:" + contentHash;
    }

    private String buildTimeKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "time:" + userKey;
    }

    private String buildNewKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "new:" + userKey;
    }

    private boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
