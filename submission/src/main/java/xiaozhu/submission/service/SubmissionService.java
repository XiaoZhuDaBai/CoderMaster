package xiaozhu.submission.service;

import xiaozhu.common.message.JudgeResultMessage;
import xiaozhu.submission.model.dto.RunCaseRequest;
import xiaozhu.submission.model.dto.RunCaseResultResponse;
import xiaozhu.submission.model.dto.SubmissionCreateRequest;
import xiaozhu.submission.model.dto.SubmissionPageRequest;
import xiaozhu.submission.model.dto.SubmissionPageResponse;
import xiaozhu.submission.model.dto.SubmissionResponse;

public interface SubmissionService {

    SubmissionResponse createSubmission(SubmissionCreateRequest request);

    String forwardRunCase(RunCaseRequest request);

    void applyJudgeResult(JudgeResultMessage resultMessage);

    SubmissionResponse getSubmission(Long submissionId);

    RunCaseResultResponse getRunCaseResult(String requestId);

    /**
     * 分页查询用户的提交记录
     * @param request 分页请求参数
     * @return 分页响应
     */
    SubmissionPageResponse getSubmissionPage(SubmissionPageRequest request);
}
