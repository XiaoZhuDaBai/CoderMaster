package xiaozhu.judge.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import xiaozhu.common.dto.SandboxExecuteRequest;
import xiaozhu.common.dto.SandboxExecuteResponse;
import xiaozhu.judge.codesandbox.CodeSandbox;
import xiaozhu.judge.model.ExecuteCodeRequest;
import xiaozhu.judge.model.ExecuteCodeResponse;

import java.util.List;

/**
 * 代码沙箱 REST 接口
 *
 * 供 ai-service 等内部服务调用，用于：
 * - 运行对数器生成测试数据
 * - 仅执行代码，不进行判题和结果校验
 */
@Slf4j
@RestController
@RequestMapping("/api/sandbox")
@RequiredArgsConstructor
public class SandboxController {

    private final CodeSandbox codeSandbox;

    /**
     * 仅执行代码（不判题）
     *
     * @param request 执行请求（代码 + 语言 + 输入）
     * @return 执行结果（outputList 为每个用例的标准输出）
     */
    @PostMapping("/runCode")
    public SandboxExecuteResponse runCode(@RequestBody SandboxExecuteRequest request) {
        log.info("收到沙箱执行请求，语言={}", request.getLanguage());
        try {
            ExecuteCodeRequest innerRequest = new ExecuteCodeRequest();
            innerRequest.setCode(request.getCode());
            innerRequest.setLanguage(request.getLanguage());
            innerRequest.setUserInput(request.getUserInput());
            ExecuteCodeResponse innerResponse = codeSandbox.userTestCode(innerRequest);

            SandboxExecuteResponse response = new SandboxExecuteResponse();
            response.setSuccess(innerResponse.getExitCode() != null && innerResponse.getExitCode() == 0L);
            response.setExitCode(innerResponse.getExitCode());
            response.setRawOutputList(innerResponse.getOutputList());
            if (innerResponse.getJudgeInfo() != null) {
                response.setErrorMessages(innerResponse.getJudgeInfo().getErrorMessages());
            }

            log.info("沙箱执行完成，success={}, exitCode={}, outputCount={}",
                    response.isSuccess(), response.getExitCode(),
                    response.getRawOutputList() != null ? response.getRawOutputList().size() : 0);
            return response;
        } catch (Exception e) {
            log.error("沙箱执行失败", e);
            SandboxExecuteResponse errorResponse = new SandboxExecuteResponse();
            errorResponse.setSuccess(false);
            errorResponse.setExitCode(-1L);
            errorResponse.setErrorMessages(List.of(e.getMessage()));
            return errorResponse;
        }
    }
}
