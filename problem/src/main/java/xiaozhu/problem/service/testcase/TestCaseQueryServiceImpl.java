package xiaozhu.problem.service.testcase;

import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xiaozhu.common.constant.RedisKeyConstant;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import xiaozhu.problem.entity.QuestionTestCase;
import xiaozhu.problem.mapper.QuestionTestCaseMapper;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.DAYS;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseQueryServiceImpl implements TestCaseQueryService {

    private static final long CACHE_TTL_DAYS = 7L;
    private static final long PUBLIC_CACHE_TTL_DAYS = 30L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final QuestionTestCaseMapper questionTestCaseMapper;

    @Override
    public List<TestCaseGenerationResponse.TestCaseDetail> getTestCasesByContentHash(String contentHash) {
        if (contentHash == null || contentHash.isBlank()) {
            return Collections.emptyList();
        }

        // 1. 先查 Redis
        List<TestCaseGenerationResponse.TestCaseDetail> testCases = getFromRedis(contentHash);
        if (testCases != null && !testCases.isEmpty()) {
            log.debug("从Redis获取测试用例，contentHash={}, count={}", contentHash, testCases.size());
            return testCases;
        }

        // 2. Redis未命中，从MySQL查询
        log.info("Redis未命中，从MySQL查询测试用例，contentHash={}", contentHash);
        testCases = getFromMySQL(contentHash);

        if (testCases != null && !testCases.isEmpty()) {
            // 3. 回写Redis
            cacheToRedis(contentHash, testCases);
            return testCases;
        }

        log.warn("MySQL中也不存在测试用例，contentHash={}", contentHash);
        return Collections.emptyList();
    }

    private List<TestCaseGenerationResponse.TestCaseDetail> getFromRedis(String contentHash) {
        try {
            String redisKey = RedisKeyConstant.QUESTION_TEST_CASE_PREFIX + contentHash;
            Object cached = redisTemplate.opsForValue().get(redisKey);
            if (cached == null) {
                return null;
            }

            TestCaseGenerationResponse response;
            if (cached instanceof TestCaseGenerationResponse) {
                response = (TestCaseGenerationResponse) cached;
            } else {
                String json = JSON.toJSONString(cached);
                response = JSON.parseObject(json, TestCaseGenerationResponse.class);
            }
            return response != null ? response.getTestCases() : null;
        } catch (Exception e) {
            log.error("从Redis读取测试用例失败，contentHash={}", contentHash, e);
            return null;
        }
    }

    private List<TestCaseGenerationResponse.TestCaseDetail> getFromMySQL(String contentHash) {
        try {
            List<QuestionTestCase> testCases = questionTestCaseMapper.selectByContentHash(contentHash);
            if (testCases == null || testCases.isEmpty()) {
                return Collections.emptyList();
            }
            return testCases.stream()
                    .map(this::convertToDetail)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("从MySQL查询测试用例失败，contentHash={}", contentHash, e);
            return Collections.emptyList();
        }
    }

    private void cacheToRedis(String contentHash, List<TestCaseGenerationResponse.TestCaseDetail> testCases) {
        try {
            String redisKey = RedisKeyConstant.QUESTION_TEST_CASE_PREFIX + contentHash;
            TestCaseGenerationResponse response = new TestCaseGenerationResponse();
            response.setTestCases(testCases);

            // 动态计算 TTL：有公开用例则缓存 30 天，否则缓存 7 天
            boolean hasPublic = testCases.stream()
                    .anyMatch(tc -> tc.getIsPublic() != null && tc.getIsPublic() == 1);
            long ttlDays = hasPublic ? PUBLIC_CACHE_TTL_DAYS : CACHE_TTL_DAYS;

            redisTemplate.opsForValue().set(redisKey, response, ttlDays, TimeUnit.DAYS);
            log.info("测试用例已回写Redis，contentHash={}, count={}, ttl={}天",
                    contentHash, testCases.size(), ttlDays);
        } catch (Exception e) {
            log.error("回写Redis失败，contentHash={}", contentHash, e);
        }
    }

    private TestCaseGenerationResponse.TestCaseDetail convertToDetail(QuestionTestCase entity) {
        TestCaseGenerationResponse.TestCaseDetail detail = new TestCaseGenerationResponse.TestCaseDetail();
        detail.setCaseIndex(entity.getCaseIndex());
        detail.setInput(entity.getInput());
        detail.setExpectedOutput(entity.getExpectedOutput());
        detail.setIsPublic(entity.getIsPublic());
        detail.setTimeLimit(entity.getTimeLimit());
        detail.setMemoryLimit(entity.getMemoryLimit());
        detail.setCaseType(entity.getCaseType());
        detail.setWeight(entity.getWeight() != null ? entity.getWeight().doubleValue() : 1.0);
        detail.setGenerationSource(entity.getGenerationSource());
        detail.setVersion(entity.getVersion());
        detail.setContentHash(entity.getContentHash());
        return detail;
    }
}
