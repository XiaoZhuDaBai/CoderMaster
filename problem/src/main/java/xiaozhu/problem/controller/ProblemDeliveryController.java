package xiaozhu.problem.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import xiaozhu.common.annotation.RequireLogin;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.ProblemPageRequest;
import xiaozhu.common.dto.ProblemPageResponse;
import xiaozhu.problem.service.distribution.ProblemDeliveryService;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/problem/delivery")
public class ProblemDeliveryController {

    private final ProblemDeliveryService problemDeliveryService;

    @RequireLogin
    @GetMapping("/{userKey}")
    public ResponseResult<List<ProblemGenerationResponse>> listGeneratedProblems(@PathVariable String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return ResponseResult.fail("用户标识不能为空");
        }
        List<ProblemGenerationResponse> problems = problemDeliveryService.listProblemsSorted(userKey);
        return ResponseResult.success(problems);
    }

    @RequireLogin
    @GetMapping("/new/{userKey}")
    public ResponseResult<List<ProblemGenerationResponse>> listNewGeneratedProblems(@PathVariable String userKey) {
        if (!StringUtils.hasText(userKey)) {
            return ResponseResult.fail("用户标识不能为空");
        }
        List<ProblemGenerationResponse> problems = problemDeliveryService.listNewProblems(userKey);
        return ResponseResult.success(problems);
    }

    @RequireLogin
    @PostMapping("/paged/{userKey}")
    public ResponseResult<ProblemPageResponse> listProblemsPaged(
            @PathVariable String userKey,
            @RequestBody ProblemPageRequest request) {
        if (!StringUtils.hasText(userKey)) {
            return ResponseResult.fail("用户标识不能为空");
        }
        ProblemPageResponse response = problemDeliveryService.listProblemsPaged(userKey, request);
        return ResponseResult.success(response);
    }
}
