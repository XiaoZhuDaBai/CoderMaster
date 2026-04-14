package xiaozhu.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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
}
