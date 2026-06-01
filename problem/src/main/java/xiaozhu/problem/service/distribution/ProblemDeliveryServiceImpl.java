package xiaozhu.problem.service.distribution;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import xiaozhu.common.constant.RedisKeyConstant;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.ProblemPageRequest;
import xiaozhu.common.dto.ProblemPageResponse;
import xiaozhu.problem.entity.Question;
import xiaozhu.problem.mapper.QuestionMapper;

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
    private static final int MAX_SCAN_COUNT = 1000;

    private final RedisTemplate<String, Object> redisTemplate;
    private final QuestionMapper questionMapper;

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
        List<ProblemWithScore> items = getSortedIndexItems(userKey);
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptyList();
        }
        return fetchProblemsByHashes(items, userKey);
    }

    @Override
    public List<ProblemGenerationResponse> listProblemsSorted(String userKey) {
        return listProblems(userKey);
    }

    @Override
    public List<ProblemGenerationResponse> listNewProblems(String userKey) {
        long now = Instant.now().toEpochMilli();
        long threshold = now - HOURS.toMillis(NEW_PROBLEM_THRESHOLD_HOURS);
        
        List<ProblemWithScore> items = getSortedIndexItems(userKey);
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptyList();
        }
        
        List<ProblemWithScore> newItems = items.stream()
                .filter(item -> item.score >= threshold)
                .toList();
        
        if (newItems.isEmpty()) {
            return Collections.emptyList();
        }
        
        return fetchProblemsByHashes(newItems, userKey);
    }

    @Override
    public ProblemGenerationResponse getProblemByContentHash(String contentHash, String userKey) {
        if (!hasText(contentHash) || !hasText(userKey)) {
            return null;
        }
        String redisKey = RedisKeyConstant.QUESTION_PREFIX + userKey + ":" + contentHash;
        Object cached = redisTemplate.opsForValue().get(redisKey);
        if (cached != null) {
            return convertToProblem(cached);
        }
        
        Question question = questionMapper.selectOne(
            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                .eq(Question::getContentHash, contentHash)
        );
        if (question == null) {
            return null;
        }
        
        ProblemGenerationResponse response = convertQuestionToResponse(question);
        redisTemplate.opsForValue().set(redisKey, response, CACHE_TTL_DAYS, DAYS);
        return response;
    }

    @Override
    public ProblemPageResponse listProblemsPaged(String userKey, ProblemPageRequest request) {
        int page = request.getPage() != null && request.getPage() > 0 ? request.getPage() : 1;
        int pageSize = request.getPageSize() != null && request.getPageSize() > 0 ? request.getPageSize() : 10;
        int offset = (page - 1) * pageSize;
        
        boolean hasFilter = hasText(request.getSearchKeyword()) 
                || request.getDifficulty() != null 
                || (request.getTagNames() != null && !request.getTagNames().isEmpty());
        
        if (!hasFilter) {
            return queryPagedWithoutFilter(userKey, request, page, pageSize, offset);
        } else {
            return queryPagedWithFilter(userKey, request, page, pageSize);
        }
    }

    private ProblemPageResponse queryPagedWithoutFilter(String userKey, ProblemPageRequest request, 
            int page, int pageSize, int offset) {
        String sortedKey = buildSortedIndexKey(userKey);
        
        long total = Optional.ofNullable(redisTemplate.opsForZSet().zCard(sortedKey)).orElse(0L);
        if (total == 0) {
            return emptyPageResponse(page, pageSize);
        }
        
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(sortedKey, offset, offset + pageSize - 1);
        
        if (CollectionUtils.isEmpty(tuples)) {
            return emptyPageResponse(page, pageSize);
        }
        
        List<ProblemWithScore> items = tuples.stream()
                .map(t -> new ProblemWithScore(t.getValue().toString(), t.getScore() != null ? t.getScore().longValue() : 0L))
                .toList();
        
        List<ProblemGenerationResponse> problems = fetchProblemsByHashes(items, userKey);
        
        ProblemPageResponse response = new ProblemPageResponse();
        response.setProblems(problems);
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages((int) Math.ceil((double) total / pageSize));
        response.setAvailableTags(Collections.emptyList());
        
        return response;
    }

    private ProblemPageResponse queryPagedWithFilter(String userKey, ProblemPageRequest request,
            int page, int pageSize) {
        List<ProblemWithScore> allItems = getSortedIndexItems(userKey);
        if (CollectionUtils.isEmpty(allItems)) {
            return emptyPageResponse(page, pageSize);
        }
        
        List<ProblemGenerationResponse> allProblems = fetchProblemsByHashes(allItems, userKey);
        
        Map<String, ProblemGenerationResponse> problemMap = new HashMap<>();
        Set<String> allTags = new HashSet<>();
        
        for (int i = 0; i < allItems.size() && i < allProblems.size(); i++) {
            ProblemGenerationResponse p = allProblems.get(i);
            if (p != null) {
                p.setGeneratedAt(allItems.get(i).score);
                p.setIsNew(isNewProblem(allItems.get(i).score));
                problemMap.put(allItems.get(i).hash, p);
                if (p.getTagNames() != null) {
                    allTags.addAll(p.getTagNames());
                }
            }
        }
        
        List<ProblemWithScore> filtered = allItems.stream()
                .filter(item -> {
                    ProblemGenerationResponse p = problemMap.get(item.hash);
                    return p != null && matchesFilter(p, request);
                })
                .toList();
        
        long total = filtered.size();
        int totalPages = (int) Math.ceil((double) total / pageSize);
        int fromIndex = Math.min((page - 1) * pageSize, (int) total);
        int toIndex = Math.min(fromIndex + pageSize, (int) total);
        
        List<ProblemGenerationResponse> pagedList = fromIndex < toIndex
                ? filtered.subList(fromIndex, toIndex).stream()
                        .map(item -> problemMap.get(item.hash))
                        .filter(Objects::nonNull)
                        .toList()
                : Collections.emptyList();
        
        ProblemPageResponse response = new ProblemPageResponse();
        response.setProblems(pagedList);
        response.setTotal(total);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages(totalPages);
        response.setAvailableTags(new ArrayList<>(allTags).stream().sorted().toList());
        
        return response;
    }

    private List<ProblemWithScore> getSortedIndexItems(String userKey) {
        String sortedKey = buildSortedIndexKey(userKey);
        Set<ZSetOperations.TypedTuple<Object>> tuples = redisTemplate.opsForZSet()
                .reverseRangeWithScores(sortedKey, 0, MAX_SCAN_COUNT - 1);
        
        if (CollectionUtils.isEmpty(tuples)) {
            return Collections.emptyList();
        }
        
        return tuples.stream()
                .map(t -> new ProblemWithScore(t.getValue().toString(), t.getScore() != null ? t.getScore().longValue() : 0L))
                .toList();
    }

    private List<ProblemGenerationResponse> fetchProblemsByHashes(List<ProblemWithScore> items, String userKey) {
        if (CollectionUtils.isEmpty(items)) {
            return Collections.emptyList();
        }
        
        List<String> hashes = items.stream().map(item -> item.hash).toList();
        
        List<String> detailKeys = hashes.stream()
                .map(this::buildDetailKey)
                .toList();
        
        List<Object> cachedList = redisTemplate.opsForValue().multiGet(detailKeys);
        
        List<String> missHashes = new ArrayList<>();
        List<Integer> missIndices = new ArrayList<>();
        
        for (int i = 0; i < hashes.size(); i++) {
            Object cached = cachedList != null && i < cachedList.size() ? cachedList.get(i) : null;
            if (cached == null) {
                missHashes.add(hashes.get(i));
                missIndices.add(i);
            }
        }
        
        Map<String, ProblemGenerationResponse> resultMap = new HashMap<>();
        Map<String, Integer> hashToIndex = new HashMap<>();
        for (int i = 0; i < hashes.size(); i++) {
            hashToIndex.put(hashes.get(i), i);
        }
        
        for (int i = 0; i < hashes.size(); i++) {
            Object cached = cachedList != null && i < cachedList.size() ? cachedList.get(i) : null;
            if (cached != null) {
                ProblemGenerationResponse p = convertToProblem(cached);
                if (p != null) {
                    p.setGeneratedAt(items.get(i).score);
                    p.setIsNew(isNewProblem(items.get(i).score));
                    resultMap.put(hashes.get(i), p);
                }
            }
        }
        
        if (!missHashes.isEmpty()) {
            List<Question> questions = questionMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                    .in(Question::getContentHash, missHashes)
            );
            
            List<String> foundHashes = new ArrayList<>();
            Map<String, ProblemGenerationResponse> missMap = new HashMap<>();
            
            for (Question q : questions) {
                ProblemGenerationResponse p = convertQuestionToResponse(q);
                p.setGeneratedAt(0L);
                missMap.put(q.getContentHash(), p);
                foundHashes.add(q.getContentHash());
            }
            
            List<String> notFoundHashes = missHashes.stream()
                    .filter(h -> !foundHashes.contains(h))
                    .toList();
            
            if (!notFoundHashes.isEmpty()) {
                cleanupStaleIndex(userKey, notFoundHashes);
            }
            
            Map<String, ProblemGenerationResponse> writeBackMap = new HashMap<>();
            for (String hash : missHashes) {
                ProblemGenerationResponse p = missMap.get(hash);
                if (p != null) {
                    Integer idx = hashToIndex.get(hash);
                    if (idx != null && idx < items.size()) {
                        p.setGeneratedAt(items.get(idx).score);
                        p.setIsNew(isNewProblem(items.get(idx).score));
                    }
                    resultMap.put(hash, p);
                    writeBackMap.put(hash, p);
                }
            }
            
            if (!writeBackMap.isEmpty()) {
                writeBackToRedis(writeBackMap);
            }
        }
        
        List<ProblemGenerationResponse> result = new ArrayList<>();
        for (ProblemWithScore item : items) {
            result.add(resultMap.get(item.hash));
        }
        
        return result;
    }

    private void cleanupStaleIndex(String userKey, List<String> staleHashes) {
        String sortedKey = buildSortedIndexKey(userKey);
        for (String hash : staleHashes) {
            redisTemplate.opsForZSet().remove(sortedKey, hash);
            log.warn("清理过期索引，userKey={}, contentHash={}", userKey, hash);
        }
    }

    private void writeBackToRedis(Map<String, ProblemGenerationResponse> data) {
        for (Map.Entry<String, ProblemGenerationResponse> entry : data.entrySet()) {
            String detailKey = buildDetailKey(entry.getKey());
            redisTemplate.opsForValue().set(detailKey, entry.getValue(), DETAIL_TTL_DAYS, DAYS);
        }
    }

    private boolean matchesFilter(ProblemGenerationResponse problem, ProblemPageRequest request) {
        if (hasText(request.getSearchKeyword())) {
            String keyword = request.getSearchKeyword().toLowerCase();
            boolean matches = false;
            if (problem.getTitle() != null && problem.getTitle().toLowerCase().contains(keyword)) {
                matches = true;
            }
            if (problem.getDescription() != null && problem.getDescription().toLowerCase().contains(keyword)) {
                matches = true;
            }
            if (problem.getTagNames() != null) {
                for (String tag : problem.getTagNames()) {
                    if (tag.toLowerCase().contains(keyword)) {
                        matches = true;
                        break;
                    }
                }
            }
            if (!matches) {
                return false;
            }
        }
        
        if (request.getDifficulty() != null) {
            if (!request.getDifficulty().equals(problem.getDifficulty())) {
                return false;
            }
        }
        
        if (request.getTagNames() != null && !request.getTagNames().isEmpty()) {
            if (problem.getTagNames() == null) {
                return false;
            }
            boolean hasTag = false;
            for (String tag : problem.getTagNames()) {
                if (request.getTagNames().contains(tag)) {
                    hasTag = true;
                    break;
                }
            }
            if (!hasTag) {
                return false;
            }
        }
        
        return true;
    }

    private ProblemPageResponse emptyPageResponse(int page, int pageSize) {
        ProblemPageResponse response = new ProblemPageResponse();
        response.setProblems(Collections.emptyList());
        response.setTotal(0);
        response.setPage(page);
        response.setPageSize(pageSize);
        response.setTotalPages(0);
        response.setAvailableTags(Collections.emptyList());
        return response;
    }

    private void saveToDeliveryBucket(String userKey, String contentHash,
            ProblemGenerationResponse response, boolean isNew, long generatedAt) {
        response.setGeneratedAt(generatedAt);
        response.setIsNew(isNew);
        response.setContentHash(contentHash);
        
        String sortedKey = buildSortedIndexKey(userKey);
        redisTemplate.opsForZSet().add(sortedKey, contentHash, generatedAt);
        redisTemplate.expire(sortedKey, CACHE_TTL_DAYS, DAYS);
        
        String detailKey = buildDetailKey(contentHash);
        redisTemplate.opsForValue().set(detailKey, response, DETAIL_TTL_DAYS, DAYS);
        
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
        response.setContentHash(question.getContentHash());
        return response;
    }

    private String buildProblemKey(String userKey, String contentHash) {
        return RedisKeyConstant.QUESTION_PREFIX + userKey + ":" + contentHash;
    }

    private String buildSortedIndexKey(String userKey) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "sorted:" + userKey;
    }

    private String buildDetailKey(String contentHash) {
        return RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "detail:" + contentHash;
    }

    private boolean hasText(String str) {
        return str != null && !str.isBlank();
    }

    private record ProblemWithScore(String hash, long score) {}
}
