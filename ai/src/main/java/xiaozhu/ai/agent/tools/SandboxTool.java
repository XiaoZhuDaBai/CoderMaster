package xiaozhu.ai.agent.tools;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xiaozhu.ai.memory.CaseSearchService;
import xiaozhu.ai.memory.FailureCase;
import xiaozhu.ai.memory.SuccessCase;
import xiaozhu.ai.metrics.TokenUsageListener;
import xiaozhu.common.dto.SandboxExecuteRequest;
import xiaozhu.common.dto.SandboxExecuteResponse;
import xiaozhu.common.feign.JudgeSandboxFeignClient;

import java.util.List;

/**
 * Agent 沙箱执行工具
 * 提供给 Agent 调用的工具：
 * 1. executeCode - 在沙箱中执行代码（支持多语言）
 * 2. runGeneratorCode - 执行测试用例生成器并自动获取 expectedOutput
 * 3. verifySolutionCode - 验证 solutionCode 是否正确（通过样例测试）
 * 4. getSimilarSuccessCases - 获取相似成功案例
 * 5. getSimilarFailureCases - 获取相似失败案例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SandboxTool {

    private final JudgeSandboxFeignClient judgeSandboxFeignClient;
    private final CaseSearchService caseSearchService;

    private static final String DEFAULT_LANGUAGE = "java";
    
    // Token 预算阈值（可配置，默认 80%）
    private static final double TOKEN_BUDGET_WARNING_THRESHOLD = 0.8;
    private static final double TOKEN_BUDGET_ABORT_THRESHOLD = 0.95;

    /**
     * 检查 Token 预算，如果接近超限则抛出异常中断 Agent 执行
     * 在每个工具调用开始时执行，防止无效消耗
     */
    private void checkTokenBudget() {
        long currentTokens = TokenUsageListener.getSessionTotalTokens();
        // 假设总预算为 100000（可在配置中调整）
        long budgetLimit = 100000;
        
        if (currentTokens > budgetLimit * TOKEN_BUDGET_ABORT_THRESHOLD) {
            log.warn("[SandboxTool] Token 预算即将超限，消耗={}, 预算={}, 阈值={}",
                    currentTokens, budgetLimit, TOKEN_BUDGET_ABORT_THRESHOLD);
            throw new IllegalStateException("Token 预算即将超限，当前消耗 " + currentTokens + "，请尽快完成当前任务");
        } else if (currentTokens > budgetLimit * TOKEN_BUDGET_WARNING_THRESHOLD) {
            log.warn("[SandboxTool] Token 消耗警告，消耗={}, 预算={}, 阈值={}",
                    currentTokens, budgetLimit, TOKEN_BUDGET_WARNING_THRESHOLD);
        }
    }

    /**
     * 在沙箱中执行代码
     *
     * @param code 要执行的代码
     * @param userInput 用户输入
     * @param language 编程语言，默认 java，可选 python/cpp/go 等
     */
    @Tool(name = "executeCode")
    public String executeCode(
            @P("code") String code,
            @P("userInput") String userInput,
            @P("language") String language) {

        // 检查 Token 预算，防止超限
        checkTokenBudget();
        
        String actualLang = StrUtil.isBlank(language) ? DEFAULT_LANGUAGE : language.trim().toLowerCase();
        log.info("[SandboxTool] 执行代码，代码长度={}, 输入长度={}, 语言={}",
                code != null ? code.length() : 0, userInput != null ? userInput.length() : 0, actualLang);

        try {
            SandboxExecuteRequest request = new SandboxExecuteRequest();
            request.setCode(code);
            request.setLanguage(actualLang);
            request.setUserInput(userInput);

            SandboxExecuteResponse response = judgeSandboxFeignClient.runCode(request);

            if (response == null) {
                return toErrorResult("沙箱响应为空");
            }

            if (!response.isSuccess()) {
                String errors = response.getErrorMessages() != null
                        ? String.join("\n", response.getErrorMessages()) : "unknown";
                return toErrorResult("执行失败: " + errors);
            }

            List<String> outputs = response.getRawOutputList();
            if (outputs == null || outputs.isEmpty()) {
                return toErrorResult("输出为空");
            }

            String rawOutput = outputs.getFirst();
            log.info("[SandboxTool] 执行成功，语言={}，输出长度={}", actualLang, rawOutput.length());

            return JSON.toJSONString(new SandboxResult(
                    true,
                    rawOutput,
                    response.getExitCode(),
                    null,
                    null
            ));

        } catch (Exception e) {
            log.error("[SandboxTool] 执行异常: {}", e.getMessage(), e);
            return toErrorResult("执行异常: " + e.getMessage());
        }
    }

    /**
     * 执行测试用例生成器并自动获取 expectedOutput
     * 流程：
     * 1. 执行 generatorCode 获取 rawInput
     * 2. 执行 solutionCode 获取 expectedOutput
     * 3. 返回测试用例列表 [{input, expectedOutput}]
     *
     * @param generatorCode 测试用例生成器代码
     * @param solutionCode 参考解题代码（用于计算 expectedOutput）
     * @param language 编程语言，默认 java
     * @param maxTestCases 最大生成用例数，默认 5
     */
    @Tool(name = "runGeneratorCode")
    public String runGeneratorCode(
            @P("generatorCode") String generatorCode,
            @P("solutionCode") String solutionCode,
            @P("language") String language,
            @P("maxTestCases") Integer maxTestCases) {

        // 检查 Token 预算，防止超限
        checkTokenBudget();
        
        // 验证 generatorCode 类名
        if (generatorCode != null && generatorCode.contains("public class")) {
            if (!generatorCode.contains("public class Main")) {
                log.warn("[SandboxTool] generatorCode 包含非 Main 的 public class，可能导致编译错误");
                return JSON.toJSONString(new RunGeneratorResult(false, 0, 
                        "generatorCode 错误：必须使用 public class Main，禁止其他 public class"));
            }
        }
        
        String actualLang = StrUtil.isBlank(language) ? DEFAULT_LANGUAGE : language.trim().toLowerCase();
        int actualMax = maxTestCases != null && maxTestCases > 0 ? maxTestCases : 5;
        log.info("[SandboxTool] runGeneratorCode，语言={}, maxTestCases={}", actualLang, actualMax);

        try {
            List<TestCaseItem> testCases = new java.util.ArrayList<>();

            // 循环生成多个测试用例
            for (int i = 0; i < actualMax; i++) {
                // Step 1: 执行 generatorCode 获取 rawInput
                String genResult = executeCode(generatorCode, null, actualLang);

                if (genResult == null || genResult.contains("\"success\":false")) {
                    log.warn("[SandboxTool] runGeneratorCode caseIndex={}: generatorCode 执行失败，跳过", i);
                    continue;
                }

                String rawInput = extractOutput(genResult);
                if (rawInput == null || rawInput.isBlank()) {
                    log.warn("[SandboxTool] runGeneratorCode caseIndex={}: generatorCode 输出为空，跳过", i);
                    continue;
                }

                // Step 2: 执行 solutionCode 获取 expectedOutput
                String solResult = executeCode(solutionCode, rawInput, actualLang);

                if (solResult == null || solResult.contains("\"success\":false")) {
                    log.warn("[SandboxTool] runGeneratorCode caseIndex={}: solutionCode 执行失败，跳过", i);
                    continue;
                }

                String expectedOutput = extractOutput(solResult);
                if (expectedOutput == null || expectedOutput.isBlank()) {
                    log.warn("[SandboxTool] runGeneratorCode caseIndex={}: solutionCode 输出为空，跳过", i);
                    continue;
                }

                testCases.add(new TestCaseItem(i, rawInput.trim(), expectedOutput.trim()));
                log.info("[SandboxTool] runGeneratorCode caseIndex={} 成功，input长度={}, output长度={}",
                        i, rawInput.length(), expectedOutput.length());
            }

            if (testCases.isEmpty()) {
                return JSON.toJSONString(new RunGeneratorResult(false, 0, "所有用例生成均失败"));
            }

            log.info("[SandboxTool] runGeneratorCode 完成，成功生成 {} 个测试用例", testCases.size());
            return JSON.toJSONString(new RunGeneratorResult(true, testCases.size(), testCases));

        } catch (Exception e) {
            log.error("[SandboxTool] runGeneratorCode 异常: {}", e.getMessage(), e);
            return toErrorResult("生成测试用例异常: " + e.getMessage());
        }
    }

    /**
     * 验证 solutionCode 是否正确（通过样例测试）
     */
    @Tool(name = "verifySolutionCode")
    public String verifySolutionCode(
            @P("solutionCode") String solutionCode,
            @P("samples") String samples) {

        // 检查 Token 预算，防止超限
        checkTokenBudget();
        
        // 验证 solutionCode 类名
        if (solutionCode != null && solutionCode.contains("public class")) {
            if (!solutionCode.contains("public class Main")) {
                log.warn("[SandboxTool] solutionCode 包含非 Main 的 public class，可能导致编译错误");
                return toErrorResult("solutionCode 错误：必须使用 public class Main，禁止其他 public class");
            }
        }
        
        log.info("[SandboxTool] 验证 solutionCode，代码长度={}, 样例数量={}",
                solutionCode != null ? solutionCode.length() : 0,
                samples != null ? "已提供" : "无");

        try {
            if (StrUtil.isBlank(solutionCode)) {
                return toErrorResult("solutionCode 为空");
            }

            // 解析样例
            List<SampleCase> sampleCases = parseSamples(samples);
            if (sampleCases.isEmpty()) {
                return JSON.toJSONString(new VerifyResult(false, 0, 0, "无有效样例，无法验证"));
            }

            int passCount = 0;
            int totalCount = sampleCases.size();
            int crashCount = 0;

            for (int i = 0; i < sampleCases.size(); i++) {
                SampleCase sample;
                try {
                    sample = sampleCases.get(i);
                } catch (Exception e) {
                    log.warn("[SandboxTool] 样例#{} 解析异常，跳过: {}", i + 1, e.getMessage());
                    crashCount++;
                    continue;
                }

                String output;
                try {
                    output = executeCode(solutionCode, sample.input, DEFAULT_LANGUAGE);
                } catch (Exception e) {
                    log.warn("[SandboxTool] 样例#{} 执行异常，跳过: {}", i + 1, e.getMessage());
                    crashCount++;
                    continue;
                }

                if (output == null || output.contains("\"success\":false")) {
                    log.warn("[SandboxTool] 样例#{} 执行失败（编译错误或崩溃）", i + 1);
                    crashCount++;
                    continue;
                }

                // 比较输出
                String actualOutput = extractOutput(output);
                if (actualOutput != null && actualOutput.trim().equals(sample.expectedOutput.trim())) {
                    passCount++;
                } else {
                    log.warn("[SandboxTool] 样例#{} 输出不匹配，期望={}, 实际={}",
                            i + 1,
                            sample.expectedOutput.length() > 50 ? sample.expectedOutput.substring(0, 50) + "..." : sample.expectedOutput,
                            actualOutput != null && actualOutput.length() > 50 ? actualOutput.substring(0, 50) + "..." : actualOutput);
                }
            }

            boolean allPassed = passCount == totalCount && crashCount == 0;
            String message;
            if (crashCount > 0) {
                message = "有 " + crashCount + " 个样例执行崩溃，验证失败";
            } else if (!allPassed) {
                message = "部分失败";
            } else {
                message = "全部通过";
            }
            log.info("[SandboxTool] 验证完成，通过 {}/{}，崩溃 {}，结果={}", passCount, totalCount, crashCount, message);

            return JSON.toJSONString(new VerifyResult(allPassed, passCount, totalCount, message));

        } catch (Exception e) {
            log.error("[SandboxTool] 验证异常: {}", e.getMessage(), e);
            return toErrorResult("验证异常: " + e.getMessage());
        }
    }

    /**
     * 获取相似成功案例
     */
    @Tool(name = "getSimilarSuccessCases")
    public String getSimilarSuccessCases(
            @P("problemType") String problemType,
            @P("keywords") String keywords,
            @P("limit") Integer limit) {

        int actualLimit = limit != null && limit > 0 ? limit : 3;
        try {
            List<SuccessCase> cases = caseSearchService.findSimilarSuccessCases(problemType, keywords, actualLimit);
            String summary = caseSearchService.buildSuccessCaseSummary(cases);
            log.info("[SandboxTool] 获取成功案例，找到 {} 条", cases.size());
            return summary;
        } catch (Exception e) {
            log.error("[SandboxTool] 获取成功案例异常: {}", e.getMessage());
            return "获取相似案例失败: " + e.getMessage();
        }
    }

    /**
     * 获取相似失败案例
     */
    @Tool(name = "getSimilarFailureCases")
    public String getSimilarFailureCases(
            @P("problemType") String problemType,
            @P("failureReason") String failureReason,
            @P("limit") Integer limit) {

        int actualLimit = limit != null && limit > 0 ? limit : 3;
        try {
            List<FailureCase> cases = caseSearchService.findSimilarFailureCases(problemType, failureReason, actualLimit);
            String summary = caseSearchService.buildFailureCaseSummary(cases);
            log.info("[SandboxTool] 获取失败案例，找到 {} 条", cases.size());
            return summary;
        } catch (Exception e) {
            log.error("[SandboxTool] 获取失败案例异常: {}", e.getMessage());
            return "获取失败案例失败: " + e.getMessage();
        }
    }

    private List<SampleCase> parseSamples(String samples) {
        if (StrUtil.isBlank(samples)) {
            return List.of();
        }
        try {
            return JSON.parseArray(samples, SampleCase.class);
        } catch (Exception e) {
            log.warn("[SandboxTool] 解析样例失败: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractOutput(String jsonResult) {
        if (jsonResult == null || jsonResult.isBlank()) {
            return null;
        }
        try {
            SandboxResult result = JSON.parseObject(jsonResult, SandboxResult.class);
            if (result != null && result.output() != null) {
                return result.output();
            }
        } catch (Exception ignored) {
            // JSON 解析失败，尝试直接返回原字符串（兼容边缘情况）
        }
        // 不是预期的 JSON 格式时，返回原字符串作为输出
        return jsonResult.trim();
    }

    private String toErrorResult(String message) {
        return JSON.toJSONString(new SandboxResult(false, null, -1, message, null));
    }

    // 内部类
    private record SandboxResult(boolean success, String output, long exitCode, String error, String metadata) {}
    private record VerifyResult(boolean passed, int passCount, int totalCount, String message) {}
    private record SampleCase(String input, String expectedOutput) {}
    private record RunGeneratorResult(boolean success, int count, Object data) {}
    private record TestCaseItem(int caseIndex, String input, String expectedOutput) {}
}
