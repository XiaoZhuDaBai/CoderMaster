package xiaozhu.ai.controller;

import lombok.extern.slf4j.Slf4j;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhu.ai.model.ProblemGenerationRequest;
import xiaozhu.ai.service.problem.ProblemService;
import xiaozhu.common.annotation.RateLimit;
import xiaozhu.common.annotation.RequireLogin;
import xiaozhu.common.comm.ResponseResult;

import jakarta.validation.Valid;

/**
 * 题目生成接口
 */
@Slf4j
@RestController
@RequestMapping("/api/problem")
@RequiredArgsConstructor
public class ProblemController {

    private final ProblemService problemService;

    /**
     * 生成题目（异步）
     * 需要登录，限流：60秒内最多10次请求
     * 题目生成在后台异步执行，前端可通过轮询 /api/problem/delivery/new/{userKey} 获取新生成的题目
     */
    @PostMapping("/generate")
    @RequireLogin(message = "请先登录后再生成题目")
    @RateLimit(windowSize = 60, maxRequests = 10, message = "题目生成请求过于频繁，请稍后再试")
    public ResponseResult<String> generateProblem(@RequestBody @Valid ProblemGenerationRequest request) {
        if (request == null) {
            return ResponseResult.fail("请求参数不能为空");
        }
        
        // 异步执行题目生成任务
        problemService.generateBatchProblemsAsync(request);
        
        log.info("题目生成任务已提交，userKey={}, number={}", 
                request.getUserUuid() != null ? request.getUserUuid() : "unknown", 
                request.getNumber());
        
        return ResponseResult.success("题目生成任务已提交，请通过轮询接口获取生成结果");
    }
}
