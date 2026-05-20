package xiaozhu.ai.agent.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import xiaozhu.ai.agent.config.CrossValidationConfig;
import xiaozhu.common.dto.SandboxExecuteRequest;
import xiaozhu.common.dto.SandboxExecuteResponse;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.feign.JudgeSandboxFeignClient;

import java.util.ArrayList;
import java.util.List;

/**
 * 交叉验证结果记录（compareAndRecord 返回值）
 *
 * @param type    情况类型：①③②⑤
 * @param needsFix 该用例的 expectedOutput 是否需要修正
 */
record CompareResult(String type, boolean needsFix) {}

/**
 * 交叉验证服务 —— 用第二个独立 AI 模型对 solutionCode 做语义层面的"二次理解"。
 *
 * <p>核心价值：打破"同一个 AI 模型既生成 solutionCode 又验证 solutionCode"的循环依赖。
 * 如果主模型（Agent）因题意理解偏差而生成错误的 solutionCode，交叉验证模型（独立训练/架构）
 * 有更高的概率从不同角度发现这个错误。</p>
 *
 * <p>双 AI 交叉验证流程：
 * <ol>
 *   <li>将 problem 描述（含题目自带的 2 个 testCases）发送给交叉验证模型</li>
 *   <li>让交叉验证模型独立生成该题目的解题代码 solutionCodeB（不看 AI#1 的 solutionCode）</li>
 *   <li>分别用 solutionCodeA（主）和 solutionCodeB（交叉）执行题目自带的 input，对比输出</li>
 *   <li>判断 sandboxA vs sandboxB vs expectedOutput 的三者关系，决定 expectedOutput 是否可信</li>
 * </ol>
 *
 * <p>5 种结果判断：
 * <ul>
 *   <li>情况①: sandboxA == sandboxB == expectedOutput → 三方一致，expectedOutput 高度可信</li>
 *   <li>情况②: sandboxA == sandboxB != expectedOutput → 两个 AI 一致但与题目不符，expectedOutput 错误，需要修正</li>
 *   <li>情况③: sandboxA == expectedOutput != sandboxB → AI#1 和题目一致，以 AI#1 为准</li>
 *   <li>情况④: sandboxA != expectedOutput && sandboxA == sandboxB → 同情况②，expectedOutput 错误，需要修正</li>
 *   <li>情况⑤: sandboxA != expectedOutput && sandboxB != expectedOutput && sandboxA != sandboxB → 严重分歧，用 AI#1</li>
 * </ul>
 */
@Slf4j
@Service
public class CrossValidationService {

    private final ChatModel crossValidationModel;
    private final JudgeSandboxFeignClient judgeSandboxFeignClient;
    private final CrossValidationConfig config;

    private static final ObjectMapper OM = new ObjectMapper();

    public CrossValidationService(
            @Qualifier("crossValidationChatModelPrototype") ChatModel crossValidationModel,
            JudgeSandboxFeignClient judgeSandboxFeignClient,
            CrossValidationConfig config) {
        this.crossValidationModel = crossValidationModel;
        this.judgeSandboxFeignClient = judgeSandboxFeignClient;
        this.config = config;
    }

    /**
     * 双 AI 交叉验证：对比两个独立 AI 对题目自带 testCases 的执行结果。
     *
     * @param problemTitle        题目标题
     * @param problemDescription  题目描述
     * @param inputDesc           输入描述
     * @param outputDesc          输出描述
     * @param solutionCodeA       主模型生成的 solutionCode（AI#1）
     * @param problemTestCases    题目自带的 testCases（来自 ProblemGenerationResponse，含 expectedOutput）
     * @return 验证结果封装，含修正后的 expectedOutput、交叉验证 solutionCode、分歧详情
     */
    public CrossValidationResult validate(String problemTitle,
                                          String problemDescription,
                                          String inputDesc,
                                          String outputDesc,
                                          String solutionCodeA,
                                          List<ProblemGenerationResponse.TestCase> problemTestCases) {
        if (!config.isEnabled()) {
            log.debug("[CrossValidator] 交叉验证未启用，跳过");
            return CrossValidationResult.skipped("cross_validation_disabled");
        }

        if (crossValidationModel == null) {
            log.warn("[CrossValidator] 交叉验证模型未配置，跳过");
            return CrossValidationResult.skipped("model_not_configured");
        }

        if (solutionCodeA == null || solutionCodeA.isBlank()) {
            log.warn("[CrossValidator] 主模型 solutionCode 为空，跳过");
            return CrossValidationResult.skipped("solution_code_empty");
        }

        if (problemTestCases == null || problemTestCases.isEmpty()) {
            log.warn("[CrossValidator] 题目自带 testCases 为空，跳过");
            return CrossValidationResult.skipped("no_problem_test_cases");
        }

        log.info("[CrossValidator] 开始双 AI 交叉验证，题目={}, testCase数量={}",
                problemTitle, problemTestCases.size());
        long start = System.currentTimeMillis();

        try {
            // Step 1: 让交叉验证模型独立生成解题代码（不看 solutionCodeA）
            String solutionCodeB = generateIndependentSolution(
                    problemTitle, problemDescription, inputDesc, outputDesc);

            if (solutionCodeB == null || solutionCodeB.isBlank()) {
                log.warn("[CrossValidator] 交叉验证模型未能生成独立 solutionCode，跳过");
                return CrossValidationResult.skipped("no_independent_solution_generated");
            }

            log.info("[CrossValidator] 交叉验证模型生成独立 solutionCode 成功，长度={}", solutionCodeB.length());

            // Step 2: 对每个题目自带的 testCase，执行双 AI 对比
            int passCount = 0;
            int totalCount = problemTestCases.size();
            List<String> details = new ArrayList<>();
            List<String> discrepancies = new ArrayList<>();
            boolean hasOriginalExpectedOutputError = false;

            for (int i = 0; i < problemTestCases.size(); i++) {
                ProblemGenerationResponse.TestCase tc = problemTestCases.get(i);
                String input = tc.getInput();
                String originalExpected = tc.getExpectedOutput();

                if (input == null || input.isBlank() || originalExpected == null || originalExpected.isBlank()) {
                    details.add(String.format("用例#%d：跳过（input 或 expectedOutput 为空）", i + 1));
                    continue;
                }

                // 沙箱执行
                String sandboxA = runSandbox(solutionCodeA, input);
                String sandboxB = runSandbox(solutionCodeB, input);

                CompareResult cr = compareCase(tc, i, sandboxA, sandboxB, details, discrepancies);
                if ("①".equals(cr.type()) || "③".equals(cr.type())) {
                    passCount++;
                }
                if (cr.needsFix()) {
                    hasOriginalExpectedOutputError = true;
                }
            }

            boolean allPassed = passCount == totalCount;
            long elapsed = System.currentTimeMillis() - start;

            CrossValidationResult result = new CrossValidationResult(
                    true,
                    allPassed,
                    passCount,
                    totalCount,
                    String.format("双AI交叉验证完成，through=%d/%d，expectedOutput错误=%s，耗时=%dms",
                            passCount, totalCount, hasOriginalExpectedOutputError, elapsed),
                    details,
                    elapsed,
                    solutionCodeB,
                    hasOriginalExpectedOutputError,
                    discrepancies
            );

            log.info("[CrossValidator] 双AI交叉验证完成，through={}/{}, expectedOutput需修正={}, elapsed={}ms",
                    passCount, totalCount, hasOriginalExpectedOutputError, elapsed);

            return result;

        } catch (Exception e) {
            log.error("[CrossValidator] 双AI交叉验证异常: {}", e.getMessage(), e);
            return CrossValidationResult.error("交叉验证异常: " + e.getMessage());
        }
    }

    /**
     * 对比单个测试用例的沙箱执行结果。
     *
     * @return CompareResult，type 为情况类型（①③②⑤），needsFix 表示 expectedOutput 是否需要修正
     */
    private CompareResult compareCase(ProblemGenerationResponse.TestCase tc, int index,
                                     String sandboxA, String sandboxB,
                                     List<String> details, List<String> discrepancies) {
        String input = tc.getInput();
        if (input == null || input.isBlank()) {
            details.add(String.format("用例#%d：跳过（input 为空）", index + 1));
            discrepancies.add(String.format("用例#%d：input 为空", index + 1));
            return new CompareResult("⑤", false);
        }
        if (tc.getExpectedOutput() == null || tc.getExpectedOutput().isBlank()) {
            details.add(String.format("用例#%d：跳过（expectedOutput 为空）", index + 1));
            discrepancies.add(String.format("用例#%d：expectedOutput 为空", index + 1));
            return new CompareResult("⑤", false);
        }
        String originalExpected = tc.getExpectedOutput().trim();

        boolean aSuccess = sandboxA != null && !sandboxA.isBlank();
        boolean bSuccess = sandboxB != null && !sandboxB.isBlank();

        // 两个都失败 → 无法验证
        if (!aSuccess && !bSuccess) {
            details.add(String.format("用例#%d：两方沙箱执行均失败，无法验证", index + 1));
            discrepancies.add(String.format("用例#%d：两方沙箱执行均失败", index + 1));
            return new CompareResult("⑤", false);
        }

        String outA = aSuccess ? sandboxA.trim() : "【执行失败】";
        String outB = bSuccess ? sandboxB.trim() : "【执行失败】";

        // 情况⑤：两者都与 expectedOutput 不一致，且两者也不一致（严重分歧）
        if (aSuccess && bSuccess
                && !outA.equals(originalExpected)
                && !outB.equals(originalExpected)
                && !outA.equals(outB)) {
            String msg = String.format("用例#%d：严重分歧 → AI#1输出=%s, AI#2输出=%s, 题目expectedOutput=%s → 保守使用AI#1输出",
                    index + 1, truncate(outA), truncate(outB), truncate(originalExpected));
            details.add(msg);
            discrepancies.add(String.format("用例#%d[情况⑤]：严重分歧，sandboxA=%s, sandboxB=%s, expectedOutput=%s",
                    index + 1, outA, outB, originalExpected));
            return new CompareResult("⑤", false);
        }

        // 情况①：三方完全一致
        if (aSuccess && outA.equals(originalExpected)
                && bSuccess && outA.equals(outB)) {
            String msg = String.format("用例#%d：①三方一致，output=%s", index + 1, truncate(outA));
            details.add(msg);
            return new CompareResult("①", false);
        }

        // 情况②：两个 AI 一致，但与题目 expectedOutput 不符 → expectedOutput 错误
        // （情况②和情况④条件相同，只需一个 if 覆盖）
        if (aSuccess && bSuccess && outA.equals(outB)) {
            String msg = String.format("用例#%d：②/④ expectedOutput错误！两个AI输出一致(%s)但与题目不符(%s) → 用正确结果替换",
                    index + 1, truncate(outA), truncate(originalExpected));
            details.add(msg);
            discrepancies.add(String.format("用例#%d[情况②/④]：expectedOutput错误，题目expectedOutput=%s，修正为=%s",
                    index + 1, originalExpected, outA));
            return new CompareResult("②", true);
        }

        // 情况③：AI#1 和 expectedOutput 一致，AI#2 不一致
        if (aSuccess && outA.equals(originalExpected)) {
            if (!bSuccess) {
                details.add(String.format("用例#%d：③ AI#1与expectedOutput一致，AI#2执行失败 → 保持expectedOutput", index + 1));
            } else {
                details.add(String.format("用例#%d：③ AI#1与expectedOutput一致(%s)，AI#2不一致(%s) → 保持expectedOutput",
                        index + 1, truncate(outA), truncate(outB)));
            }
            return new CompareResult("③", false);
        }

        // 兜底：无法归类（如 AI#1 不一致但 AI#2 也不一致，且两者互相不一致）
        details.add(String.format("用例#%d：兜底，AI#1=%s, AI#2=%s, expectedOutput=%s",
                index + 1, truncate(outA), truncate(outB), truncate(originalExpected)));
        return new CompareResult("⑤", false);
    }

    /**
     * 让交叉验证模型独立生成解题代码（不看 solutionCodeA）。
     */
    private String generateIndependentSolution(String title, String description,
                                               String inputDesc, String outputDesc) throws Exception {
        String prompt = buildIndependentSolutionPrompt(title, description, inputDesc, outputDesc);

        dev.langchain4j.model.chat.response.ChatResponse response =
                crossValidationModel.chat(List.of(
                        dev.langchain4j.data.message.UserMessage.from(prompt)));

        if (response == null || response.aiMessage() == null
                || response.aiMessage().toString().isBlank()) {
            log.warn("[CrossValidator] 交叉验证模型返回为空");
            return null;
        }

        String raw = response.aiMessage().toString();
        return extractSolutionCode(raw);
    }

    private String buildIndependentSolutionPrompt(String title, String description,
                                                  String inputDesc, String outputDesc) {
        return """
                你是一个严格的算法评审专家。请仔细阅读下面的题目，独立生成一份正确的 Java 解题代码。

                【题目】
                题目标题：%s

                题目描述：
                %s

                输入描述：
                %s

                输出描述：
                %s

                【任务】
                1. 基于上述题目，独立生成一份 Java 解题代码（类名必须为 Main，无 package 声明）
                2. 将代码以 JSON 格式返回：
                   {"solutionCode": "完整的Java代码字符串"}
                3. 不要参考任何外部信息，仅基于题目描述推导
                4. 代码必须包含 public class Main 和 public static void main 方法
                """.formatted(
                title != null ? title : "",
                description != null ? description : "",
                inputDesc != null ? inputDesc : "",
                outputDesc != null ? outputDesc : "");
    }

    private String extractSolutionCode(String raw) {
        // 尝试多种提取策略
        String[] markers = {"```json", "```java", "```"};
        String[] keys = {"solutionCode", "code", "answer"};

        for (String marker : markers) {
            int blockStart = raw.indexOf(marker);
            if (blockStart < 0) continue;
            int contentStart = blockStart + marker.length();
            int blockEnd = raw.indexOf("```", contentStart);
            if (blockEnd <= contentStart) continue;
            String block = raw.substring(contentStart, blockEnd).trim();

            // 尝试 JSON 解析
            for (String key : keys) {
                try {
                    int braceStart = block.indexOf('{');
                    int braceEnd = block.lastIndexOf('}');
                    if (braceStart >= 0 && braceEnd > braceStart) {
                        String json = block.substring(braceStart, braceEnd + 1);
                        var map = OM.readValue(json, java.util.Map.class);
                        Object val = map.get(key);
                        if (val != null && !val.toString().isBlank()) {
                            return unescapeJsonEncodedString(val.toString());
                        }
                    }
                } catch (Exception ignored) {}
            }

            // 当作纯代码处理
            if (!block.startsWith("{")) {
                return unescapeJsonEncodedString(block);
            }
        }

        // 直接尝试 JSON { }
        int braceStart = raw.indexOf('{');
        int braceEnd = raw.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String json = raw.substring(braceStart, braceEnd + 1);
            for (String key : keys) {
                try {
                    var map = OM.readValue(json, java.util.Map.class);
                    Object val = map.get(key);
                    if (val != null && !val.toString().isBlank()) {
                        return unescapeJsonEncodedString(val.toString());
                    }
                } catch (Exception ignored) {}
            }
        }

        // 直接提取 Java 代码
        if (raw.contains("class Main") || raw.contains("public class")) {
            int classStart = raw.indexOf("class Main");
            if (classStart < 0) classStart = raw.indexOf("public class");
            if (classStart >= 0) {
                return unescapeJsonEncodedString(raw.substring(classStart).trim());
            }
        }

        return null;
    }

    /**
     * 反转义 JSON 双重编码的字符串。
     * AI 返回的 solutionCode 可能在 JSON 中被双重转义，如 \\n -> 真实换行，\\t -> 真实 tab。
     */
    private String unescapeJsonEncodedString(String code) {
        if (code == null || code.isBlank()) return code;
        return code
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private String runSandbox(String code, String userInput) {
        try {
            SandboxExecuteRequest request = new SandboxExecuteRequest();
            request.setCode(code);
            request.setLanguage("java");
            request.setUserInput(userInput != null ? userInput : "");

            SandboxExecuteResponse response = judgeSandboxFeignClient.runCode(request);
            if (response == null || !response.isSuccess()) {
                return null;
            }

            List<String> outputs = response.getRawOutputList();
            if (outputs == null || outputs.isEmpty()) {
                return "";
            }
            return outputs.getFirst();
        } catch (Exception e) {
            log.warn("[CrossValidator] 沙箱执行异常: {}", e.getMessage());
            return null;
        }
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 60 ? s.substring(0, 60) + "..." : s;
    }
}
