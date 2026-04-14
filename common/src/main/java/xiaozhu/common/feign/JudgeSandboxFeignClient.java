package xiaozhu.common.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import xiaozhu.common.dto.SandboxExecuteRequest;
import xiaozhu.common.dto.SandboxExecuteResponse;

/**
 * 判题沙箱 Feign 接口
 *
 * ai-service 通过此接口调用 judge-service 的沙箱，
 * 用于运行对数器生成测试数据（仅执行代码，不判题）。
 */
@FeignClient(
        name = "oj-judge",
        path = "/api/sandbox"
)
public interface JudgeSandboxFeignClient {

    /**
     * 仅执行代码（不判题）
     *
     * 用途：ai-service 调用对数器生成测试数据
     *
     * @param request 执行请求（包含代码、语言、stdin输入）
     * @return 执行结果（rawOutput 为标准输出）
     */
    @PostMapping("/runCode")
    SandboxExecuteResponse runCode(@RequestBody SandboxExecuteRequest request);
}
