package xiaozhu.problem.controller.internal;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.common.feign.ProblemFeignClient;
import xiaozhu.problem.entity.Question;
import xiaozhu.problem.mapper.QuestionMapper;

/**
 * 内部接口 - 供其他微服务调用
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem/internal")
public class ProblemInternalController {

    private final QuestionMapper questionMapper;

    /**
     * 根据 questionId 获取题目基本信息（供 submission 服务调用）
     */
    @GetMapping("/question/{questionId}")
    public ResponseResult<ProblemFeignClient.ProblemBasicInfo> getQuestionBasicInfo(@PathVariable Long questionId) {
        log.info("=== getQuestionBasicInfo 被调用 === questionId={}", questionId);
        Question question = questionMapper.selectById(questionId);
        log.info("=== 数据库查询结果 === question={}", question);
        if (question == null) {
            log.warn("=== 题目不存在 === questionId={}", questionId);
            return ResponseResult.fail("题目不存在");
        }
        ProblemFeignClient.ProblemBasicInfo info = new ProblemFeignClient.ProblemBasicInfo(
                question.getQuestionId(),
                question.getTitle(),
                question.getDifficulty()
        );
        log.info("=== 返回题目信息 === questionId={}, title={}, difficulty={}", 
                question.getQuestionId(), question.getTitle(), question.getDifficulty());
        return ResponseResult.success(info);
    }

    /**
     * 根据 contentHash 获取 questionId（供 submission 服务调用）
     */
    @GetMapping("/question/id/{contentHash}")
    public ResponseResult<Long> getQuestionIdByContentHash(@PathVariable String contentHash) {
        log.info("=== getQuestionIdByContentHash 被调用 === contentHash={}", contentHash);
        if (contentHash == null || contentHash.isBlank()) {
            return ResponseResult.fail("contentHash 不能为空");
        }
        Question question = questionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Question>()
                        .eq(Question::getContentHash, contentHash)
        );
        if (question == null) {
            log.warn("=== 题目不存在 === contentHash={}", contentHash);
            return ResponseResult.fail("题目不存在");
        }
        log.info("=== 返回 questionId === questionId={}", question.getQuestionId());
        return ResponseResult.success(question.getQuestionId());
    }

    /**
     * 根据 questionId 获取完整题目信息（供 submission 服务调用）
     */
    @GetMapping("/question/detail/{questionId}")
    public ResponseResult<ProblemFeignClient.ProblemDetailInfo> getQuestionDetail(@PathVariable Long questionId) {
        log.info("=== getQuestionDetail 被调用 === questionId={}", questionId);
        Question question = questionMapper.selectById(questionId);
        if (question == null) {
            log.warn("=== 题目不存在 === questionId={}", questionId);
            return ResponseResult.fail("题目不存在");
        }
        ProblemFeignClient.ProblemDetailInfo detail = new ProblemFeignClient.ProblemDetailInfo(
                question.getQuestionId(),
                question.getQuestionCode(),
                question.getTitle(),
                question.getDifficulty(),
                question.getDescription(),
                question.getInputDesc(),
                question.getOutputDesc(),
                question.getExamples(),
                question.getTimeLimit(),
                question.getMemoryLimit()
        );
        log.info("=== 返回题目详情 === questionId={}, title={}", questionId, question.getTitle());
        return ResponseResult.success(detail);
    }
}
