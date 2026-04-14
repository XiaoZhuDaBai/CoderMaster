package xiaozhu.submission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import xiaozhu.common.annotation.RequireLogin;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.submission.model.dto.RunCaseRequest;
import xiaozhu.submission.model.dto.RunCaseResultResponse;
import xiaozhu.submission.model.dto.SubmissionCreateRequest;
import xiaozhu.submission.model.dto.SubmissionResponse;
import xiaozhu.submission.service.SubmissionService;

@RestController
@RequestMapping("/api/submissions")
@RequiredArgsConstructor
public class SubmissionController {

    private final SubmissionService submissionService;

    /**
     * 正式提交 - 落库并投递判题任务
     */
    @RequireLogin
    @PostMapping
    public ResponseResult<SubmissionResponse> submit(@Valid @RequestBody SubmissionCreateRequest request) {
        return ResponseResult.success(submissionService.createSubmission(request));
    }

    /**
     * 查询提交结果 - 轮询接口
     */
    @RequireLogin
    @GetMapping("/{submissionId}")
    public ResponseResult<SubmissionResponse> getSubmission(@PathVariable Long submissionId) {
        SubmissionResponse response = submissionService.getSubmission(submissionId);
        return ResponseResult.success(response);
    }

    /**
     * 运行案例 - 直接转发判题
     */
    @RequireLogin
    @PostMapping("/run")
    public ResponseResult<String> runCase(@Valid @RequestBody RunCaseRequest request) {
        String requestId = submissionService.forwardRunCase(request);
        return ResponseResult.success("运行任务已入队", requestId);
    }

    /**
     * 查询运行案例结果 - 轮询接口
     */
    @RequireLogin
    @GetMapping("/run/{requestId}")
    public ResponseResult<RunCaseResultResponse> getRunCaseResult(@PathVariable String requestId) {
        RunCaseResultResponse response = submissionService.getRunCaseResult(requestId);
        return ResponseResult.success(response);
    }
}
