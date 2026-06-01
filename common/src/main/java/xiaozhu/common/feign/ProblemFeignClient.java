package xiaozhu.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.common.dto.TestCaseGenerationResponse;

import java.util.List;

/**
 * 题目服务 Feign 接口
 *
 */
@FeignClient(
        name = "oj-problem",
        path = "/api/problem"
)
public interface ProblemFeignClient {

    /**
     * 根据 contentHash 获取测试用例
     */
    @GetMapping("/testcase/contentHash/{contentHash}")
    List<TestCaseGenerationResponse.TestCaseDetail> getTestCasesByContentHash(
            @PathVariable("contentHash") String contentHash);

    /**
     * 根据 questionId 获取题目信息（用于 submission 服务获取题目标题和难度）
     */
    @GetMapping("/internal/question/{questionId}")
    ResponseResult<ProblemBasicInfo> getProblemBasicInfo(@PathVariable("questionId") Long questionId);

    /**
     * 根据 contentHash 获取 questionId（用于 submission 服务获取真正的自增 questionId）
     */
    @GetMapping("/internal/question/id/{contentHash}")
    ResponseResult<Long> getQuestionIdByContentHash(@PathVariable("contentHash") String contentHash);

    /**
     * 根据 questionId 获取完整题目详情（用于 submission 服务获取完整题目信息）
     */
    @GetMapping("/internal/question/detail/{questionId}")
    ResponseResult<ProblemDetailInfo> getQuestionDetail(@PathVariable("questionId") Long questionId);
    
    /**
     * 题目基本信息 DTO
     */
    record ProblemBasicInfo(Long questionId, String title, Integer difficulty) {}

    /**
     * 题目详情信息 DTO（用于 submission 服务获取完整题目信息）
     */
    record ProblemDetailInfo(
            Long questionId,
            String questionCode,
            String title,
            Integer difficulty,
            String description,
            String inputDesc,
            String outputDesc,
            String examples,
            Integer timeLimit,
            Integer memoryLimit
    ) {}
}
