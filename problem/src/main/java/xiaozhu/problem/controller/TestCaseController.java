package xiaozhu.problem.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import xiaozhu.problem.service.testcase.TestCaseQueryService;

import java.util.List;

/**
 * 测试用例查询 Controller
 */
@Slf4j
@RestController
@RequestMapping("/api/problem/testcase")
@RequiredArgsConstructor
public class TestCaseController {

    private final TestCaseQueryService testCaseQueryService;

    /**
     * 根据 contentHash 获取测试用例
     * 流程：Redis → 未命中查MySQL → 回写Redis
     */
    @GetMapping("/contentHash/{contentHash}")
    public List<TestCaseGenerationResponse.TestCaseDetail> getByContentHash(@PathVariable String contentHash) {
        log.info("收到测试用例查询请求，contentHash={}", contentHash);
        return testCaseQueryService.getTestCasesByContentHash(contentHash);
    }
}
