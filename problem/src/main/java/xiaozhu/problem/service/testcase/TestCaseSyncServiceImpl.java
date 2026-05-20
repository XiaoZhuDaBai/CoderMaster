package xiaozhu.problem.service.testcase;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhu.common.constant.RedisKeyConstant;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import xiaozhu.common.message.TestCaseSyncMessage;
import xiaozhu.problem.entity.Question;
import xiaozhu.problem.entity.QuestionTestCase;
import xiaozhu.problem.mapper.QuestionMapper;
import xiaozhu.problem.mapper.QuestionTestCaseMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TestCaseSyncServiceImpl implements TestCaseSyncService {

    private final QuestionTestCaseMapper questionTestCaseMapper;
    private final QuestionMapper questionMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncTestCases(TestCaseSyncMessage message) {
        if (message == null || message.getContentHash() == null || message.getTestCases() == null) {
            log.warn("收到空的测试用例同步消息，已忽略");
            return;
        }

        String contentHash = message.getContentHash();
        String userKey = message.getUserKey();
        List<TestCaseGenerationResponse.TestCaseDetail> testCases = message.getTestCases();

        log.info("收到测试用例同步消息，contentHash={}, userKey={}, count={}", contentHash, userKey, testCases.size());

        try {
            // 遍历保存或更新测试用例
            for (TestCaseGenerationResponse.TestCaseDetail detail : testCases) {
                saveOrUpdateTestCase(contentHash, detail);
            }

            log.info("测试用例同步完成，contentHash={}, count={}", contentHash, testCases.size());

            // 更新 delivery bucket 中的题目（追加测试用例）
            if (userKey != null && !userKey.isBlank()) {
                updateDeliveryBucketWithTestCases(userKey, contentHash, testCases);
            }
        } catch (Exception e) {
            log.error("测试用例同步失败，contentHash={}", contentHash, e);
            throw e;
        }
    }

    private void saveOrUpdateTestCase(String contentHash, TestCaseGenerationResponse.TestCaseDetail detail) {
        Long questionId = getQuestionIdByContentHash(contentHash);
        if (questionId == null) {
            log.warn("未找到对应题目，无法同步测试用例，contentHash={}", contentHash);
            return;
        }

        LambdaQueryWrapper<QuestionTestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QuestionTestCase::getContentHash, contentHash)
               .eq(QuestionTestCase::getCaseIndex, detail.getCaseIndex());
        QuestionTestCase existing = questionTestCaseMapper.selectOne(wrapper);

        if (existing != null) {
            existing.setQuestionId(questionId);
            existing.setInput(detail.getInput());
            existing.setExpectedOutput(detail.getExpectedOutput());
            existing.setIsPublic(detail.getIsPublic());
            existing.setCaseType(detail.getCaseType());
            existing.setWeight(detail.getWeight() != null ? BigDecimal.valueOf(detail.getWeight()) : BigDecimal.ONE);
            existing.setGenerationSource(detail.getGenerationSource());
            existing.setVersion(detail.getVersion() != null ? detail.getVersion() : 1);
            existing.setTimeLimit(detail.getTimeLimit());
            existing.setMemoryLimit(detail.getMemoryLimit());
            existing.setUpdateTime(LocalDateTime.now());
            questionTestCaseMapper.updateTestCase(existing);
            log.debug("更新测试用例，caseId={}, caseIndex={}", existing.getCaseId(), detail.getCaseIndex());
        } else {
            QuestionTestCase entity = new QuestionTestCase();
            entity.setQuestionId(questionId);
            entity.setContentHash(contentHash);
            entity.setCaseIndex(detail.getCaseIndex());
            entity.setInput(detail.getInput());
            entity.setExpectedOutput(detail.getExpectedOutput());
            entity.setIsPublic(detail.getIsPublic());
            entity.setCaseType(detail.getCaseType());
            entity.setWeight(detail.getWeight() != null ? BigDecimal.valueOf(detail.getWeight()) : BigDecimal.ONE);
            entity.setGenerationSource(detail.getGenerationSource());
            entity.setVersion(detail.getVersion() != null ? detail.getVersion() : 1);
            entity.setTimeLimit(detail.getTimeLimit());
            entity.setMemoryLimit(detail.getMemoryLimit());
            entity.setCreateTime(LocalDateTime.now());
            entity.setUpdateTime(LocalDateTime.now());
            questionTestCaseMapper.insertTestCase(entity);
            log.debug("新增测试用例，caseIndex={}", detail.getCaseIndex());
        }
    }

    private Long getQuestionIdByContentHash(String contentHash) {
        Question question = questionMapper.selectOne(
            new LambdaQueryWrapper<Question>()
                .eq(Question::getContentHash, contentHash)
        );
        return question != null ? question.getQuestionId() : null;
    }

    private void updateDeliveryBucketWithTestCases(String userKey, String contentHash,
            List<TestCaseGenerationResponse.TestCaseDetail> testCases) {
        try {
            // 新结构：详情 Key
            String detailKey = RedisKeyConstant.QUESTION_DELIVERY_PREFIX + "detail:" + contentHash;
            Object raw = redisTemplate.opsForValue().get(detailKey);
            if (raw == null) {
                log.warn("delivery bucket 中未找到题目，跳过追加测试用例，userKey={}, contentHash={}", userKey, contentHash);
                return;
            }

            ProblemGenerationResponse problem;
            if (raw instanceof ProblemGenerationResponse p) {
                problem = p;
            } else {
                problem = JSON.parseObject(JSON.toJSONString(raw), ProblemGenerationResponse.class);
            }

            List<ProblemGenerationResponse.TestCase> tcList = testCases.stream()
                    .map(tc -> {
                        ProblemGenerationResponse.TestCase item = new ProblemGenerationResponse.TestCase();
                        item.setCaseIndex(tc.getCaseIndex());
                        item.setInput(tc.getInput());
                        item.setExpectedOutput(tc.getExpectedOutput());
                        item.setIsPublic(tc.getIsPublic());
                        item.setCaseType(tc.getCaseType());
                        item.setWeight(tc.getWeight());
                        item.setTimeLimit(tc.getTimeLimit());
                        item.setMemoryLimit(tc.getMemoryLimit());
                        return item;
                    })
                    .toList();
            problem.setTestCases(tcList);

            redisTemplate.opsForValue().set(detailKey, problem);
            log.info("delivery bucket 题目已追加测试用例，userKey={}, contentHash={}, count={}",
                    userKey, contentHash, testCases.size());
        } catch (Exception e) {
            log.error("更新 delivery bucket 测试用例失败，userKey={}, contentHash={}", userKey, contentHash, e);
        }
    }
}
