package xiaozhu.submission.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import xiaozhu.common.annotation.RequireLogin;
import xiaozhu.common.comm.ResponseResult;
import xiaozhu.common.eenum.JudgeStatus;
import xiaozhu.submission.model.dto.RunCaseRequest;
import xiaozhu.submission.model.dto.RunCaseResultResponse;
import xiaozhu.submission.model.dto.SubmissionCreateRequest;
import xiaozhu.submission.model.dto.SubmissionPageRequest;
import xiaozhu.submission.model.dto.SubmissionPageResponse;
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

    /**
     * 分页查询用户的提交记录
     */
    @RequireLogin
    @GetMapping("/page")
    public ResponseResult<SubmissionPageResponse> getSubmissionPage(
            @RequestParam Long userId,
            @RequestParam(required = false, defaultValue = "1") Integer pageNum,
            @RequestParam(required = false, defaultValue = "6") Integer pageSize,
            @RequestParam(required = false) String questionTitle,
            @RequestParam(required = false) String language,
            @RequestParam(required = false) JudgeStatus judgeStatus) {
        SubmissionPageRequest request = new SubmissionPageRequest();
        request.setUserId(userId);
        request.setPageNum(pageNum);
        request.setPageSize(pageSize);
        request.setQuestionTitle(questionTitle);
        request.setLanguage(language);
        request.setJudgeStatus(judgeStatus);
        return ResponseResult.success(submissionService.getSubmissionPage(request));
    }
}
