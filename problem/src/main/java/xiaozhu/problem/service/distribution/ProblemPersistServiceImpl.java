package xiaozhu.problem.service.distribution;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.problem.entity.Question;
import xiaozhu.problem.mapper.QuestionMapper;

import java.time.LocalDateTime;

import static xiaozhu.common.constant.RedisKeyConstant.QUESTION_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProblemPersistServiceImpl implements ProblemPersistService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final QuestionMapper questionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long persistToMySQL(String userKey, String contentHash) {
        if (!hasText(userKey) || !hasText(contentHash)) {
            log.warn("用户标识或内容哈希为空，无法持久化题目 userKey={}, contentHash={}", userKey, contentHash);
            return null;
        }

        // 先检查题目是否已存在
        Question existing = questionMapper.selectOne(
            new LambdaQueryWrapper<Question>()
                .eq(Question::getContentHash, contentHash)
        );
        if (existing != null) {
            log.debug("题目已存在，无需重复持久化，contentHash={}, questionId={}", contentHash, existing.getQuestionId());
            return existing.getQuestionId();
        }

        // 从 Redis 读取题目内容
        String sourceKey = buildProblemKey(userKey, contentHash);
        Object cached = redisTemplate.opsForValue().get(sourceKey);
        if (cached == null) {
            log.warn("Redis 未找到题目数据，无法持久化 userKey={}, contentHash={}", userKey, contentHash);
            return null;
        }

        ProblemGenerationResponse response = convertToProblem(cached);
        if (response == null) {
            log.warn("题目数据转换失败，无法持久化 userKey={}, contentHash={}", userKey, contentHash);
            return null;
        }

        try {
            Question question = convertToQuestionEntity(response, contentHash);
            questionMapper.insert(question);
            log.info("题目持久化成功，contentHash={}, questionId={}", contentHash, question.getQuestionId());
            return question.getQuestionId();
        } catch (Exception e) {
            log.error("题目持久化失败，contentHash={}", contentHash, e);
            throw e;
        }
    }

    @Override
    public void deleteSourceKey(String userKey, String contentHash) {
        if (!hasText(userKey) || !hasText(contentHash)) {
            return;
        }
        String sourceKey = buildProblemKey(userKey, contentHash);
        redisTemplate.delete(sourceKey);
        log.debug("已删除题目源数据 key，userKey={}, contentHash={}", userKey, contentHash);
    }

    private String buildProblemKey(String userKey, String contentHash) {
        return QUESTION_PREFIX + userKey + ":" + contentHash;
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

    private Question convertToQuestionEntity(ProblemGenerationResponse response, String contentHash) {
        Question question = new Question();
        String ts = String.valueOf(System.currentTimeMillis()).substring(3);
        int rand = (int) (Math.random() * 10000);
        question.setQuestionCode(String.format("P%s%04d", ts, rand));
        question.setTitle(sanitizeText(response.getTitle()));
        question.setDescription(sanitizeText(response.getDescription()));
        question.setInputDesc(sanitizeText(response.getInputDesc()));
        question.setOutputDesc(sanitizeText(response.getOutputDesc()));
        question.setExamples(sanitizeJsonString(response.getExamples()));
        question.setTimeLimit(response.getTimeLimit() != null ? response.getTimeLimit() : 1000);
        question.setMemoryLimit(response.getMemoryLimit() != null ? response.getMemoryLimit() : 256);
        question.setQuestionType(0);
        question.setDifficulty(response.getDifficulty() != null ? response.getDifficulty() : 1);
        question.setStatus(1);
        question.setVersion(1);
        question.setContentHash(contentHash);
        question.setCreateTime(LocalDateTime.now());
        question.setUpdateTime(LocalDateTime.now());
        question.setPublishedTime(LocalDateTime.now());
        return question;
    }

    /**
     * 清理非法 JSON 字符，移除 MySQL JSON 类型不接受的无效 UTF-8 序列
     */
    private String sanitizeJsonString(String json) {
        if (json == null || json.isBlank()) {
            return json;
        }
        String sanitized = json.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "");
        try {
            JSON.parseObject(sanitized);
            return sanitized;
        } catch (Exception e) {
            log.warn("JSON 校验失败，尝试修复，原始长度={}", sanitized.length());
            return sanitizeJsonStringAggressive(sanitized);
        }
    }

    /**
     * 激进清理：对每个字符做 UTF-8 有效性校验，只保留合法字符
     */
    private String sanitizeJsonStringAggressive(String json) {
        StringBuilder sb = new StringBuilder();
        byte[] bytes = json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int i = 0;
        while (i < bytes.length) {
            int b = bytes[i] & 0xFF;
            int len;
            if ((b & 0x80) == 0) {
                len = 1;
            } else if ((b & 0xE0) == 0xC0 && i + 1 < bytes.length && (bytes[i + 1] & 0xC0) == 0x80) {
                len = 2;
            } else if ((b & 0xF0) == 0xE0 && i + 2 < bytes.length
                    && (bytes[i + 1] & 0xC0) == 0x80 && (bytes[i + 2] & 0xC0) == 0x80) {
                len = 3;
            } else if ((b & 0xF8) == 0xF0 && i + 3 < bytes.length
                    && (bytes[i + 1] & 0xC0) == 0x80 && (bytes[i + 2] & 0xC0) == 0x80
                    && (bytes[i + 3] & 0xC0) == 0x80) {
                len = 4;
            } else {
                i++;
                continue;
            }
            sb.append(new String(bytes, i, len, java.nio.charset.StandardCharsets.UTF_8));
            i += len;
        }
        String result = sb.toString();
        try {
            JSON.parseObject(result);
            return result;
        } catch (Exception e2) {
            log.error("JSON 无法修复，返回空字符串，长度={}", result.length());
            return "[]";
        }
    }

    /**
     * 清理普通文本字段中的非法控制字符
     */
    private String sanitizeText(String text) {
        if (text == null || text.isBlank()) {
            return text;
        }
        return text.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
    }

    private boolean hasText(String str) {
        return str != null && !str.isBlank();
    }
}
