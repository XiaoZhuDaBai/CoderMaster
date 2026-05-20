package xiaozhu.ai.agent.service;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import xiaozhu.ai.agent.config.AgentConfig;

import xiaozhu.ai.agent.config.CrossValidationConfig;
import xiaozhu.ai.agent.tools.MemoryTool;
import xiaozhu.ai.agent.tools.SandboxTool;
import xiaozhu.ai.exception.AiErrorType;
import xiaozhu.ai.exception.AiGenerationException;
import xiaozhu.ai.metrics.TokenUsageListener;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.feign.JudgeSandboxFeignClient;
import xiaozhu.common.dto.SandboxExecuteRequest;
import xiaozhu.common.dto.SandboxExecuteResponse;
import xiaozhu.common.constant.RabbitMQConstants;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import xiaozhu.common.message.ProblemExpectedOutputFixMessage;

/**
 * 测试用例生成 Agent 服务
 *
 * 基于 LangChain4j 的 ReAct 模式，通过工具调用自动：
 * 1. 分析题目类型
 * 2. 检索记忆库
 * 3. 调用沙箱生成代码
 * 4. 评审测试用例
 * 5. 记录结果
 */
@Slf4j
@Service
public class TestCaseGenerationAgentService {

    private final ChatModel chatModel;
    private final SandboxTool sandboxTool;
    private final MemoryTool memoryTool;
    private final AgentConfig agentConfig;
    private final CrossValidationConfig crossValidationConfig;
    private final CrossValidationService crossValidationService;
    private final JudgeSandboxFeignClient judgeSandboxFeignClient;
    private final RabbitTemplate rabbitTemplate;
    /** 预构建的 Agent 单例，避免每次生成都重新构建 */
    private final TestCaseAgent agent;
    /** 解决方案生成提示词（用于生成参考 solutionCode） */
    private final String solutionCodePromptTemplate;
    /** ChatMemory 保留的最大消息数（防止上下文超出模型限制） */
    private static final int MAX_CHAT_MEMORY_MESSAGES = 50;

    public TestCaseGenerationAgentService(
            @Qualifier("chatModelPrototype") ChatModel chatModel,
            SandboxTool sandboxTool,
            MemoryTool memoryTool,
            AgentConfig agentConfig,
            CrossValidationConfig crossValidationConfig,
            CrossValidationService crossValidationService,
            JudgeSandboxFeignClient judgeSandboxFeignClient,
            RabbitTemplate rabbitTemplate) {
        this.chatModel = chatModel;
        this.sandboxTool = sandboxTool;
        this.memoryTool = memoryTool;
        this.agentConfig = agentConfig;
        this.crossValidationConfig = crossValidationConfig;
        this.crossValidationService = crossValidationService;
        this.judgeSandboxFeignClient = judgeSandboxFeignClient;
        this.rabbitTemplate = rabbitTemplate;
        this.solutionCodePromptTemplate = loadPromptTemplate("prompt/solution-code-generation-prompt.txt");

        // 预构建 Agent 单例，应用 maxIterations 配置
        // 注意：langchain4j 的 maxSequentialToolsInvocations 控制单次 AI 响应中的最大工具调用次数
        // 需要设置得足够大以支持完整的测试用例生成流程（分析→检索→执行→验证→记录）
        int maxAttempts = agentConfig.getMaxIterations() > 0 ? agentConfig.getMaxIterations() : 15;
        int maxSequentialTools = Math.max(maxAttempts * 2, 50); // 最小值保护，至少 50（修复：20 -> 50）
        this.agent = AiServices.builder(TestCaseAgent.class)
                .chatModel(chatModel)
                .tools(sandboxTool, memoryTool)
                // 为每个 conversationId 创建独立的 MessageWindowChatMemory
                // 当消息数超过 MAX_CHAT_MEMORY_MESSAGES 时，自动清理旧消息
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.builder()
                        .id(memoryId)
                        .maxMessages(MAX_CHAT_MEMORY_MESSAGES)
                        .build())
                // 关键配置：设置为 maxIterations 的 2 倍，因为一次完整流程可能需要多次工具调用
                .maxSequentialToolsInvocations(maxSequentialTools)
                .build();
        log.info("[AgentService] Agent 单例构建完成，maxIterations={}, maxSequentialToolsInvocations={}, maxChatMemoryMessages={}",
                agentConfig.getMaxIterations(), maxSequentialTools, MAX_CHAT_MEMORY_MESSAGES);
    }

    private String loadPromptTemplate(String path) {
        try {
            ClassPathResource resource = new ClassPathResource(path);
            return new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("[AgentService] 无法加载提示词模板 {}，使用内置默认值", path);
            return "请生成一个正确的 solutionCode JSON。";
        }
    }

    /**
     * 生成测试用例（Agent 模式）
     *
     * @param problem 题目信息
     * @param memoryId 内存 ID，用于 ChatMemory 区分不同会话（使用题目 contentHash）
     * @return 测试用例生成结果
     */
    public TestCaseGenerationResponse generate(@MemoryId String memoryId, ProblemGenerationResponse problem) {
        log.info("[AgentService] 开始生成测试用例，memoryId={}, 题目={}, maxIterations={}, reviewPassThreshold={}, maxTokenBudget={}",
                memoryId, problem.getTitle(), agentConfig.getMaxIterations(), agentConfig.getReviewPassThreshold(),
                agentConfig.getMaxTokenBudgetPerGeneration());

        try {
            TokenUsageListener.resetSession();

            // ========== 预验证阶段：构建经过双 AI 交叉验证的参考样例 ==========
            // P0+P1 核心：
            // 1. 用 AI#1 生成 solutionCodeA
            // 2. 如果启用双 AI 验证，用 AI#2 独立生成 solutionCodeB，对比两者输出
            // 3. 判断 expectedOutput 是否可信，最终 referenceSamplesJson 必须是沙箱实际输出
            VerifiedSamples verifiedSamples = buildVerifiedReferenceSamplesJson(problem);
            String referenceSamplesJson = verifiedSamples.json();
            String primarySolutionCode = verifiedSamples.solutionCode();
            String crossValidatorSolutionCode = verifiedSamples.solutionCodeFromCrossValidator();

            if (!verifiedSamples.allPassed()) {
                log.warn("[AgentService] 参考样例存在风险，total={}, skipped={}, allPassed={}",
                        verifiedSamples.totalCount(), verifiedSamples.skippedCount(), verifiedSamples.allPassed());
            }

            // 如果双 AI 验证发现 expectedOutput 错误，立即通知 problem-service 修正缓存
            if (verifiedSamples.hasOriginalExpectedOutputError()) {
                sendExpectedOutputFixMessage(problem, verifiedSamples);
            }

            // ========== Agent 执行阶段 ==========
            // 使用预构建的 Agent 单例（已在构造函数中设置 maxAttempts）
            String budgetWarning = "";
            long preTokens = TokenUsageListener.getSessionTotalTokens();
            if (agentConfig.getMaxTokenBudgetPerGeneration() > 0
                    && preTokens > agentConfig.getMaxTokenBudgetPerGeneration() * 0.8) {
                budgetWarning = "【警告】Token 消耗已达 80%，请精简步骤，尽快完成。";
                log.warn("[AgentService] Token 消耗预警，消耗={}, 预算的 80%={}",
                        preTokens, agentConfig.getMaxTokenBudgetPerGeneration() * 0.8);
            }

            String response = this.agent.generateTestCases(memoryId, problem, referenceSamplesJson,
                    primarySolutionCode != null ? primarySolutionCode : "null",
                    crossValidatorSolutionCode != null ? crossValidatorSolutionCode : "null",
                    budgetWarning);

            long sessionTokens = TokenUsageListener.getSessionTotalTokens();
            if (agentConfig.getMaxTokenBudgetPerGeneration() > 0
                    && sessionTokens > agentConfig.getMaxTokenBudgetPerGeneration()) {
                log.warn("[AgentService] Token 消耗超出预算，消耗={}, 预算={}",
                        sessionTokens, agentConfig.getMaxTokenBudgetPerGeneration());
                throw new AiGenerationException(AiErrorType.TOKEN_BUDGET_EXCEEDED,
                        String.format("Token 预算超限: 消耗 %d > 预算 %d",
                                sessionTokens, agentConfig.getMaxTokenBudgetPerGeneration()));
            }

            log.info("[AgentService] 生成完成，会话累计 tokens: input={}, output={}, total={}",
                    TokenUsageListener.getSessionInputTokens(),
                    TokenUsageListener.getSessionOutputTokens(),
                    sessionTokens);

            return parseResponse(response);
        } finally {
            // ========== 内存清理 ==========
            // 任务完成后清理 ChatMemory，释放内存
            // 注意：需要获取该 memoryId 对应的 ChatMemory 实例并清空
            // LangChain4j 的 MessageWindowChatMemory 可以通过 memory().clear() 清理
            log.info("[AgentService] 任务完成，清理 ChatMemory，memoryId={}", memoryId);
        }
    }

    /**
     * 多轮重试生成参考 solutionCode，每次注入不同思路
     */
    private String generateReferenceSolutionWithRetry(ProblemGenerationResponse problem, int maxAttempts) {
        String lastError = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String solution = generateReferenceSolution(problem, attempt, lastError);
            // 尝试修复常见格式问题（class Main -> public class Main）
            String fixed = validateAndFixJavaCode(solution);
            if (fixed != null) {
                log.info("[AgentService] 参考 solutionCode 生成成功（attempt={}, 已自动修复格式）", attempt);
                return fixed;
            }
            lastError = "第" + attempt + "次生成的 solutionCode 格式无效（必须包含 public class Main）";
            log.warn("[AgentService] 参考 solutionCode 生成失败，attempt={}, 原因={}，原始内容={}",
                    attempt, lastError,
                    solution != null && solution.length() <= 300
                            ? solution
                            : (solution != null ? solution.substring(0, 300) + "..." : "null"));
        }
        return null;
    }

    /**
     * 生成参考 solutionCode，支持分 attempt 注入不同上下文
     */
    private String generateReferenceSolution(ProblemGenerationResponse problem, int attempt, String lastError) {
        String prompt = buildSolutionPrompt(problem, attempt, lastError);
        if (prompt == null) {
            log.warn("[AgentService] 无法构建 solutionCode 生成提示词，attempt={}", attempt);
            return null;
        }
        try {
            ChatResponse response = chatModel.chat(List.of(
                    dev.langchain4j.data.message.UserMessage.from(prompt)));

            if (response == null || response.aiMessage() == null
                    || response.aiMessage().toString().isBlank()) {
                log.warn("[AgentService] AI 生成参考 solutionCode 返回为空，attempt={}", attempt);
                return null;
            }

            String extracted = extractSolutionCodeFromResponse(response.aiMessage().toString());
            if (extracted != null) {
                log.info("[AgentService] 参考 solutionCode 提取成功，长度={}", extracted.length());
            }
            return extracted;
        } catch (Exception e) {
            log.error("[AgentService] 生成参考 solutionCode 异常，attempt={}: {}", attempt, e.getMessage());
            return null;
        }
    }

    /**
     * 构建 solution-code-generation-prompt，每次 attempt 注入不同上下文
     */
    private String buildSolutionPrompt(ProblemGenerationResponse problem, int attempt, String lastError) {
        // 构造 problem_description 和 sample_cases
        StringBuilder problemDesc = new StringBuilder();
        if (problem.getTitle() != null) {
            problemDesc.append("题目标题：").append(problem.getTitle()).append("\n\n");
        }
        if (problem.getDescription() != null) {
            problemDesc.append("题目描述：").append(problem.getDescription()).append("\n\n");
        }
        if (problem.getInputDesc() != null) {
            problemDesc.append("输入描述：").append(problem.getInputDesc()).append("\n\n");
        }
        if (problem.getOutputDesc() != null) {
            problemDesc.append("输出描述：").append(problem.getOutputDesc()).append("\n\n");
        }
        if (problem.getTagNames() != null) {
            problemDesc.append("涉及标签：").append(String.join(", ", problem.getTagNames())).append("\n\n");
        }

        List<ProblemGenerationResponse.TestCase> cases = problem.getTestCases();
        if (cases == null || cases.isEmpty()) {
            return null;
        }

        List<LinkedHashMap<String, String>> sampleList = new ArrayList<>();
        for (ProblemGenerationResponse.TestCase tc : cases) {
            if (tc.getInput() != null && tc.getExpectedOutput() != null) {
                LinkedHashMap<String, String> sample = new LinkedHashMap<>();
                sample.put("input", tc.getInput());
                sample.put("expectedOutput", tc.getExpectedOutput());
                sampleList.add(sample);
            }
        }
        String sampleJson = JSON.toJSONString(sampleList);

        String prompt = solutionCodePromptTemplate
                .replace("{{problem_description}}", problemDesc.toString())
                .replace("{{sample_cases}}", sampleJson);

        // 注入不同上下文（attempt > 1 时注入上一次错误信息，引导换思路）
        if (attempt > 1 && lastError != null && !lastError.isBlank()) {
            prompt += ("\n\n【历史调试信息】\n上一次尝试失败原因：" + lastError + "\n请换一个思路重新分析题目。\n");
        }

        return prompt;
    }

    /**
     * 简单预检：排除明显无效的代码，并自动修复常见格式问题。
     * 检查类名、main 方法、括号平衡、禁止 package 声明、无占位符。
     * 自动修复 class Main -> public class Main（省 token，避免重新生成）
     * 
     * @return 修复后的有效代码，或 null 如果无法修复
     */
    private String validateAndFixJavaCode(String code) {
        if (code == null || code.length() < 50) return null;
        if (code.contains("package ")) return null;

        // 自动修复：class Main -> public class Main
        code = fixClassModifier(code);

        // 必须包含 Main 类
        if (!code.contains("class Main")) return null;
        // 必须包含 main 方法
        if (!code.contains("public static void main") && !code.contains("static void main")) return null;

        // 括号对数平衡：{ 和 } 数量必须相等
        long openBraces = code.chars().filter(c -> c == '{').count();
        long closeBraces = code.chars().filter(c -> c == '}').count();
        if (openBraces != closeBraces) return null;

        // 排除占位符：不允许 ... 或 // TODO 或 /* ... */ 中的 ...
        String noComment = code.replaceAll("//[^\n]*", "").replaceAll("/\\*[^*]*\\*/", "");
        if (noComment.contains("...")) return null;

        return code;
    }

    /**
     * 自动修复：class Main -> public class Main
     * 匹配 class Main 前没有 public 修饰符的情况
     */
    private String fixClassModifier(String code) {
        // 匹配 "class Main" 但前面不是 "public"
        // 使用正则：(?<!public\s+)class\s+Main
        // 但 Java 的 Pattern 不支持 variable-width lookbehind，所以用替换
        String fixed = code.replaceFirst("(?<!public\\s)class\\s+Main", "public class Main");
        if (!fixed.equals(code)) {
            log.debug("[AgentService] 自动修复 class Main -> public class Main");
        }
        return fixed;
    }

    /**
     * @deprecated 使用 {@link #validateAndFixJavaCode(String)} 代替
     */
    @Deprecated
    private boolean isValidJavaCode(String code) {
        return validateAndFixJavaCode(code) != null;
    }

    /**
     * 单个用例的修正详情
     */
    private record FixDetail(int caseIndex, String originalExpectedOutput, String correctedExpectedOutput) {}

    /**
     * 参考样例验证结果封装
     *
     * @param json                                    verified sample cases JSON（含沙箱验证后的 expectedOutput）
     * @param solutionCode                            预验证阶段 AI#1 生成的 solutionCode（供 Agent 复用）
     * @param solutionCodeFromCrossValidator          交叉验证 AI#2 生成的 solutionCode（供 Agent 参考）
     * @param skippedCount                            沙箱执行跳过的样例数
     * @param totalCount                              总样例数
     * @param allPassed                               是否三方完全一致（AI#1、AI#2、expectedOutput）
     * @param hasOriginalExpectedOutputError          是否有 expectedOutput 被修正（情况②或④）
     * @param fixDetails                              被修正的用例详情列表
     */
    private record VerifiedSamples(
            String json,
            String solutionCode,
            String solutionCodeFromCrossValidator,
            int skippedCount,
            int totalCount,
            boolean allPassed,
            boolean hasOriginalExpectedOutputError,
            List<FixDetail> fixDetails
    ) {}

    /**
     * 构建经过双 AI 交叉验证的参考样例 JSON。
     *
     * <p>核心逻辑（5种情况判断）：
     * <ul>
     *   <li>① sandboxA == sandboxB == expectedOutput → 三方一致，expectedOutput 高度可信</li>
     *   <li>② sandboxA == sandboxB != expectedOutput → 两个 AI 一致但与题目不符，expectedOutput 错误，修正</li>
     *   <li>③ sandboxA == expectedOutput != sandboxB → AI#1 和题目一致，以 AI#1 为准</li>
     *   <li>④ sandboxA != expectedOutput && sandboxA == sandboxB → 同情况②，expectedOutput 错误，修正</li>
     *   <li>⑤ sandboxA != expectedOutput && sandboxB != expectedOutput && sandboxA != sandboxB → 严重分歧，用 AI#1</li>
     * </ul>
     *
     * @param problem 题目信息（含 AI 生成的 testCases）
     * @return VerifiedSamples，含 JSON、solutionCode、交叉验证结果
     */
    private VerifiedSamples buildVerifiedReferenceSamplesJson(ProblemGenerationResponse problem) {
        List<ProblemGenerationResponse.TestCase> rawCases = problem != null
                && problem.getTestCases() != null
                ? problem.getTestCases()
                : List.of();

        List<ProblemGenerationResponse.TestCase> validCases = rawCases.stream()
                .filter(tc -> tc != null
                        && tc.getInput() != null && !tc.getInput().isBlank()
                        && tc.getExpectedOutput() != null && !tc.getExpectedOutput().isBlank())
                .limit(2)
                .toList();

        if (validCases.isEmpty()) {
            log.info("[AgentService] 无有效参考样例，返回空列表");
            return new VerifiedSamples("[]", null, null, 0, 0, true, false, List.of());
        }

        int totalCount = validCases.size();
        int skippedCount = 0;

        // 第一步：多轮重试生成 AI#1 的 solutionCode
        String solutionCodeA = generateReferenceSolutionWithRetry(problem, 2);
        if (solutionCodeA == null || solutionCodeA.isBlank()) {
            log.warn("[AgentService] 无法生成 AI#1 solutionCode，使用原始 expectedOutput（未验证）");
            String json = buildRawSamplesJson(validCases);
            return new VerifiedSamples(json, null, null, 0, totalCount, false, false, List.of());
        }

        // 第二步：如果启用双 AI 交叉验证，用 AI#2 独立生成 solutionCodeB 并对比
        String solutionCodeB = null;
        boolean hasOriginalExpectedOutputError = false;
        List<LinkedHashMap<String, String>> verified = new ArrayList<>();

        if (crossValidationConfig.isEnabled()) {
            CrossValidationResult cvResult = crossValidationService.validate(
                    problem.getTitle(),
                    problem.getDescription(),
                    problem.getInputDesc(),
                    problem.getOutputDesc(),
                    solutionCodeA,
                    validCases
            );

            if (cvResult != null && cvResult.isValidated()) {
                solutionCodeB = cvResult.getSolutionCodeFromCrossValidator();
                hasOriginalExpectedOutputError = cvResult.isHasOriginalExpectedOutputError();

                // 用修正后的 expectedOutput 构建 referenceSamplesJson
                for (int i = 0; i < validCases.size(); i++) {
                    ProblemGenerationResponse.TestCase tc = validCases.get(i);
                    LinkedHashMap<String, String> map = new LinkedHashMap<>();
                    map.put("input", tc.getInput());

                    // 查找该用例的修正结果
                    String correctedOutput = null;
                    if (cvResult.getDiscrepancies() != null && i < cvResult.getDiscrepancies().size()) {
                        String discrepancy = cvResult.getDiscrepancies().get(i);
                        correctedOutput = extractCorrectedOutputFromDiscrepancy(discrepancy, solutionCodeA);
                    }
                    map.put("expectedOutput", correctedOutput != null ? correctedOutput : tc.getExpectedOutput());
                    verified.add(map);
                }

                String json = JSON.toJSONString(verified);
                boolean allPassed = cvResult.isReliable() && !hasOriginalExpectedOutputError;
                log.info("[AgentService] 双AI交叉验证完成，verified={}, allPassed={}, expectedOutput需修正={}",
                        verified.size(), allPassed, hasOriginalExpectedOutputError);

                return new VerifiedSamples(json, solutionCodeA, solutionCodeB,
                        skippedCount, totalCount, allPassed, hasOriginalExpectedOutputError,
                        buildFixDetails(cvResult, validCases, solutionCodeA));
            }
        }

        // 未启用双 AI 时，降级为单 AI 沙箱验证
        return buildVerifiedSamplesFallback(problem, validCases, solutionCodeA, totalCount);
    }

    /**
     * 单 AI 降级路径：用 AI#1 的 solutionCode 沙箱验证每个样例。
     */
    private VerifiedSamples buildVerifiedSamplesFallback(ProblemGenerationResponse problem,
            List<ProblemGenerationResponse.TestCase> validCases, String solutionCodeA, int totalCount) {
        int skippedCount = 0;
        List<LinkedHashMap<String, String>> verified = new ArrayList<>();

        for (ProblemGenerationResponse.TestCase tc : validCases) {
            String sandboxOutput = runSandbox(solutionCodeA, tc.getInput(), "java");
            if (sandboxOutput == null) {
                log.warn("[AgentService] 样例沙箱执行失败，跳过该样例");
                skippedCount++;
                continue;
            }

            String actualOutput = sandboxOutput.trim();
            if (tc.getExpectedOutput() == null) {
                log.warn("[AgentService] 样例 expectedOutput 为 null，跳过该样例");
                skippedCount++;
                continue;
            }
            String expectedOutput = tc.getExpectedOutput().trim();
            if (!actualOutput.equals(expectedOutput)) {
                log.warn("[AgentService] 样例 expectedOutput 错误！AI期望={}, 沙箱实际={}，已用正确结果替换",
                        truncate(expectedOutput), truncate(actualOutput));
            }

            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            map.put("input", tc.getInput());
            map.put("expectedOutput", actualOutput);
            verified.add(map);
        }

        if (verified.isEmpty()) {
            log.error("[AgentService] 所有参考样例验证均失败，参考样例为空");
            return new VerifiedSamples("[]", solutionCodeA, null, skippedCount, totalCount, false, false, List.of());
        }

        String json = JSON.toJSONString(verified);
        boolean allPassed = skippedCount == 0 && verified.size() == totalCount;
        log.info("[AgentService] 单AI验证完成，total={}, verified={}, skipped={}, allPassed={}",
                totalCount, verified.size(), skippedCount, allPassed);

        return new VerifiedSamples(json, solutionCodeA, null, skippedCount, totalCount, allPassed, false, List.of());
    }

    /**
     * 从交叉验证结果中提取被修正的用例详情。
     */
    private List<FixDetail> buildFixDetails(CrossValidationResult cvResult,
            List<ProblemGenerationResponse.TestCase> validCases, String solutionCodeA) {
        if (cvResult == null || cvResult.getDiscrepancies() == null) return List.of();
        List<FixDetail> fixes = new ArrayList<>();
        for (int i = 0; i < validCases.size() && i < cvResult.getDiscrepancies().size(); i++) {
            String discrepancy = cvResult.getDiscrepancies().get(i);
            if (discrepancy.contains("情况②") || discrepancy.contains("情况④")
                    || discrepancy.contains("expectedOutput错误")) {
                ProblemGenerationResponse.TestCase tc = validCases.get(i);
                if (tc == null || tc.getExpectedOutput() == null) continue;
                String original = tc.getExpectedOutput();
                String corrected = extractCorrectedOutputFromDiscrepancy(discrepancy, solutionCodeA);
                if (corrected != null) {
                    fixes.add(new FixDetail(i, original, corrected));
                }
            }
        }
        return fixes;
    }

    /**
     * 从分歧描述中提取修正后的 expectedOutput。
     */
    private String extractCorrectedOutputFromDiscrepancy(String discrepancy, String solutionCodeA) {
        if (discrepancy == null) return null;
        // 策略1：从 "修正=xxx" 提取。"修正=" 是4个字符，跳过前缀后取等号后的内容
        int idx = discrepancy.indexOf("修正=");
        if (idx >= 0) {
            String val = discrepancy.substring(idx + 4);
            int comma = val.indexOf(",");
            if (comma > 0) val = val.substring(0, comma);
            val = val.trim();
            if (!val.isEmpty()) return val;
        }
        // 策略2：从 "expectedOutput错误" 描述中提取修正值
        // 格式如: "用例#0[情况②/④]：expectedOutput错误，题目expectedOutput=..., 修正=..."
        int errIdx = discrepancy.indexOf("expectedOutput错误");
        if (errIdx >= 0) {
            int eqIdx = discrepancy.indexOf("修正=", errIdx);
            if (eqIdx >= 0) {
                String val = discrepancy.substring(eqIdx + 4).trim();
                int comma = val.indexOf(",");
                if (comma > 0) val = val.substring(0, comma);
                val = val.trim();
                if (!val.isEmpty()) return val;
            }
        }
        return null;
    }

    /**
     * 发送 expectedOutput 修正消息给 problem-service。
     */
    private void sendExpectedOutputFixMessage(ProblemGenerationResponse problem, VerifiedSamples vs) {
        if (rabbitTemplate == null) {
            log.warn("[AgentService] RabbitTemplate 未注入，跳过 expectedOutput 修正消息发送");
            return;
        }
        try {
            List<ProblemExpectedOutputFixMessage.CaseFix> caseFixes = new ArrayList<>();
            List<FixDetail> fixDetails = vs.fixDetails();
            if (fixDetails == null || fixDetails.isEmpty()) {
                log.info("[AgentService] 无需修正 expectedOutput，跳过修正消息发送");
                return;
            }
            for (FixDetail fd : fixDetails) {
                caseFixes.add(ProblemExpectedOutputFixMessage.CaseFix.builder()
                        .caseIndex(fd.caseIndex())
                        .originalExpectedOutput(fd.originalExpectedOutput())
                        .correctedExpectedOutput(fd.correctedExpectedOutput())
                        .build());
            }

            var fixMsg = ProblemExpectedOutputFixMessage.builder()
                    .contentHash(problem.getContentHash())
                    .cases(caseFixes)
                    .correctionReason("双AI交叉验证发现expectedOutput错误，两个AI一致但与题目自带expectedOutput不符")
                    .occurredAt(System.currentTimeMillis())
                    .build();
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.PROBLEM_EXPECTED_OUTPUT_FIX_EXCHANGE,
                    RabbitMQConstants.PROBLEM_EXPECTED_OUTPUT_FIX_ROUTING_KEY,
                    fixMsg);
            log.warn("[AgentService] 已发送 expectedOutput 修正消息，contentHash={}, 修正用例数={}",
                    problem.getContentHash(), caseFixes.size());
        } catch (Exception e) {
            log.error("[AgentService] 发送 expectedOutput 修正消息失败: {}", e.getMessage());
        }
    }

    /**
     * 从 AI 响应中提取 solutionCode。
     * 支持多种格式，按优先级尝试：
     * 1. ```json ... ``` 块中的 solutionCode 字段
     * 2. ```json ... ``` 块中的 code 字段
     * 3. ```json ... ``` 块中的 answer 字段
     * 4. ```json ... ``` 块中提取的 Java 代码（AI 直接给代码未包装 JSON）
     * 5. ``` ``` 块中的 solutionCode/code/answer 字段
     * 6. 直接的 { ... } JSON 块中的多个键名兜底
     * 7. 直接的 Java 代码（当所有 JSON 解析失败时）
     */
    private String extractSolutionCodeFromResponse(String response) {
        if (response == null) return null;

        // 策略1-3: ```json ... ``` 块，尝试多个键名
        String code = extractFromCodeBlock(response, "```json", new String[]{"solutionCode", "code", "answer"});
        if (code != null) return unescapeJsonEncodedString(code);

        // 策略4: ```java ... ``` 代码块，AI 忘记包装 JSON 直接给了代码
        String javaBlock = extractJavaFromCodeBlock(response);
        if (javaBlock != null) return unescapeJsonEncodedString(javaBlock);

        // 策略5: ``` ``` 块（任意语言标记），尝试多个键名
        code = extractFromCodeBlock(response, "```", new String[]{"solutionCode", "code", "answer"});
        if (code != null) return unescapeJsonEncodedString(code);

        // 策略6: 尝试直接解析 { ... } JSON 块
        int braceStart = response.indexOf('{');
        int braceEnd = response.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            String json = response.substring(braceStart, braceEnd + 1);
            for (String key : new String[]{"solutionCode", "code", "answer"}) {
                try {
                    Map<?, ?> map = new ObjectMapper().readValue(json, Map.class);
                    Object val = map.get(key);
                    if (val != null && !val.toString().isBlank()) {
                        return unescapeJsonEncodedString(val.toString());
                    }
                } catch (Exception ignored) {}
            }
        }

        // 策略7: 尝试直接的 Java 代码
        if (response.contains("class Main") || response.contains("public class")) {
            int classStart = response.indexOf("class Main");
            if (classStart < 0) classStart = response.indexOf("public class");
            if (classStart >= 0) {
                return unescapeJsonEncodedString(response.substring(classStart).trim());
            }
        }

        return null;
    }

    /**
     * 从指定语言标记的代码块中提取 solutionCode，支持多个键名。
     */
    private String extractFromCodeBlock(String response, String blockMarker, String[] keys) {
        int blockStart = response.indexOf(blockMarker);
        if (blockStart < 0) return null;
        int contentStart = blockStart + blockMarker.length();
        int blockEnd = response.indexOf("```", contentStart);
        if (blockEnd <= contentStart) return null;
        String blockContent = response.substring(contentStart, blockEnd).trim();

        // 尝试作为 JSON 解析，取指定键
        for (String key : keys) {
            try {
                Map<?, ?> map = new ObjectMapper().readValue(blockContent, Map.class);
                Object val = map.get(key);
                if (val != null && !val.toString().isBlank()) {
                    return val.toString();
                }
            } catch (Exception ignored) {}
        }

        // 如果不是 JSON，当作纯代码处理
        if (!blockContent.startsWith("{")) {
            return blockContent;
        }
        return null;
    }

    /**
     * 专门提取 ```java ... ``` 代码块中的 Java 代码。
     */
    private String extractJavaFromCodeBlock(String response) {
        int blockStart = response.indexOf("```java");
        if (blockStart < 0) {
            // 没有语言标记，尝试找 ``` 并检查内容是否像 Java 代码
            blockStart = response.indexOf("```");
            if (blockStart < 0) return null;
            int contentStart = blockStart + 3;
            int blockEnd = response.indexOf("```", contentStart);
            if (blockEnd <= contentStart) return null;
            String content = response.substring(contentStart, blockEnd).trim();
            if (content.contains("class Main") || content.contains("public class")
                    || content.contains("public static void main")) {
                return content;
            }
            return null;
        }
        int contentStart = blockStart + 7;
        int blockEnd = response.indexOf("```", contentStart);
        if (blockEnd <= contentStart) return null;
        String javaCode = response.substring(contentStart, blockEnd).trim();
        if (javaCode.contains("class Main") || javaCode.contains("public class")
                || javaCode.contains("public static void main")) {
            return javaCode;
        }
        return null;
    }

    /**
     * 直接在沙箱中执行代码，返回原始输出字符串（null 表示编译/运行失败）。
     */
    private String runSandbox(String code, String userInput, String language) {
        if (StrUtil.isBlank(code)) return null;
        String actualLang = StrUtil.isBlank(language) ? "java" : language.trim().toLowerCase();

        try {
            SandboxExecuteRequest request = new SandboxExecuteRequest();
            request.setCode(code);
            request.setLanguage(actualLang);
            request.setUserInput(userInput != null ? userInput : "");

            SandboxExecuteResponse response = judgeSandboxFeignClient.runCode(request);
            if (response == null || !response.isSuccess()) {
                String errors = response != null && response.getErrorMessages() != null
                        ? String.join("; ", response.getErrorMessages())
                        : "unknown";
                log.warn("[AgentService] 沙箱执行失败: {}", errors);
                return null;
            }

            List<String> outputs = response.getRawOutputList();
            if (outputs == null || outputs.isEmpty()) return "";
            return outputs.getFirst();
        } catch (Exception e) {
            log.warn("[AgentService] 沙箱调用异常: {}", e.getMessage());
            return null;
        }
    }

    private String buildRawSamplesJson(List<ProblemGenerationResponse.TestCase> cases) {
        List<LinkedHashMap<String, String>> simplified = new ArrayList<>();
        for (ProblemGenerationResponse.TestCase tc : cases) {
            if (tc == null) continue;
            LinkedHashMap<String, String> map = new LinkedHashMap<>();
            map.put("input", tc.getInput());
            map.put("expectedOutput", tc.getExpectedOutput() != null ? tc.getExpectedOutput() : "");
            simplified.add(map);
        }
        return JSON.toJSONString(simplified);
    }

    private String truncate(String s) {
        if (s == null) return "null";
        return s.length() > 50 ? s.substring(0, 50) + "..." : s;
    }

    private TestCaseGenerationResponse parseResponse(String response) {
        log.info("[AgentService] 解析 Agent 响应，长度={}", response != null ? response.length() : 0);

        if (response == null || response.isBlank()) {
            // Agent 返回为空，检查是否有自动提交的上下文
            return tryAutoSubmit();
        }

        try {
            String json = extractJson(response);
            if (json != null) {
                return JSON.parseObject(json, TestCaseGenerationResponse.class);
            }
        } catch (Exception e) {
            log.warn("[AgentService] JSON 解析失败: {}", e.getMessage());
        }

        // 无法解析，检查是否有自动提交的上下文
        return tryAutoSubmit();
    }

    /**
     * 尝试自动提交兜底
     * 如果 Agent 没有调用 recordSuccessCase，但 reviewTestCases 成功了，使用自动提交的上下文
     */
    private TestCaseGenerationResponse tryAutoSubmit() {
        MemoryTool.AutoSubmitContext ctx = MemoryTool.getAutoSubmitContext();
        if (ctx == null || ctx.testCasesJson() == null || ctx.testCasesJson().isBlank()) {
            throw new RuntimeException("无法解析 Agent 响应为测试用例格式，且无自动提交上下文");
        }

        log.warn("[AgentService] Agent 未调用 recordSuccessCase，启用自动提交兜底机制");
        log.info("[AgentService] 自动提交测试用例，testCasesJson 长度={}", ctx.testCasesJson().length());

        // 清除自动提交上下文，防止重复提交
        MemoryTool.clearAutoSubmitContext();

        // 解析 testCasesJson 为 TestCaseGenerationResponse
        try {
            String jsonToParse = ctx.testCasesJson();
            if (jsonToParse.trim().startsWith("[")) {
                jsonToParse = "{\"testCases\":" + jsonToParse + "}";
            }
            TestCaseGenerationResponse response = JSON.parseObject(jsonToParse, TestCaseGenerationResponse.class);

            // 设置默认的 isPublic（第一个为 SAMPLE，其余为 HIDDEN）
            if (response.getTestCases() != null) {
                for (int i = 0; i < response.getTestCases().size(); i++) {
                    TestCaseGenerationResponse.TestCaseDetail tc = response.getTestCases().get(i);
                    if (tc.getIsPublic() == null) {
                        tc.setIsPublic(i < 2 ? 1 : 0); // 前2个为样例，后面的为隐藏用例
                    }
                    if (tc.getGenerationSource() == null) {
                        tc.setGenerationSource("AI");
                    }
                    if (tc.getVersion() == null) {
                        tc.setVersion(1);
                    }
                }
            }

            return response;
        } catch (Exception e) {
            log.error("[AgentService] 解析自动提交上下文失败: {}", e.getMessage());
            throw new RuntimeException("解析自动提交的测试用例失败", e);
        }
    }

    private String extractJson(String text) {
        int jsonStart = text.indexOf("```json");
        if (jsonStart >= 0) {
            int start = jsonStart + 7;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        jsonStart = text.indexOf("```");
        if (jsonStart >= 0) {
            int start = jsonStart + 3;
            int end = text.indexOf("```", start);
            if (end > start) {
                return text.substring(start, end).trim();
            }
        }

        int braceStart = text.indexOf('{');
        int braceEnd = text.lastIndexOf('}');
        if (braceStart >= 0 && braceEnd > braceStart) {
            return text.substring(braceStart, braceEnd + 1);
        }

        return null;
    }

    /**
     * Agent 接口
     */
    public interface TestCaseAgent {

        @UserMessage("""
                为以下题目生成测试用例。

                题目信息：{{problem}}

                【参考样例】（用于 verifySolutionCode 的 samples 参数，请原样传入）
                referenceSamplesJson={{referenceSamplesJson}}

                【主解题代码】（来自预验证阶段的 AI#1，已通过沙箱验证，请优先使用）
                primarySolutionCode={{primarySolutionCode}}

                【交叉验证解题代码】（来自 AI#2 的独立推导，仅作参考）
                crossValidatorSolutionCode={{crossValidatorSolutionCode}}

                {{budgetWarning}}

                【强制执行步骤】
                1. 调用 analyzeProblemTypeLocal 进行本地规则分析（基于关键词匹配，非 AI）
                2. 调用 getSimilarSuccessCases 获取成功案例参考（必须调用，limit 默认 3）
                3. 调用 getSimilarFailureCases 获取失败教训（必须调用，limit 默认 3）
                4. 优先使用 primarySolutionCode：
                   - primarySolutionCode 不是 "null" → 直接进入步骤 5
                   - primarySolutionCode 是 "null" → 重新生成 solutionCode 并执行 verifySolutionCode
                5. 调用 verifySolutionCode 验证 solutionCode，samples 参数直接使用 referenceSamplesJson
                   - 返回 passed=true → 进入步骤 6
                   - 返回 passed=false → 分析 failedCases 中的具体错误：
                     * 运行时错误（如 EmptyStackException）→ 修复 solutionCode 中的 bug，重新执行 verifySolutionCode（最多 2 次）
                     * 逻辑错误（输出不匹配）→ 记录错误原因，使用 referenceSamplesJson 中的期望输出，继续进入步骤 6
                     * 禁止无限重试 verifySolutionCode，超过 2 次后强制继续
                6. 调用 executeCode 生成 generatorCode（language="java"）
                7. 调用 runGeneratorCode 执行 generatorCode，自动获取 testCases
                8. 调用 reviewTestCases 评审测试用例，参数：
                   - testCasesJson: 测试用例 JSON
                   - problemHash: 使用题目的 contentHash
                   - problemTitle: 使用题目的 title
                   - problemType: 从 analyzeProblemTypeLocal 获取
                   - algorithmKeyword: 从 analyzeProblemTypeLocal 获取
                   - solutionCode: 使用 primarySolutionCode

                【评审结果处理 - 关键】
                9. 如果 reviewTestCases 返回 passed=true 且 score >= 60：
                   → 【必须立即调用 recordSuccessCase】不要尝试改进或添加更多用例！
                   → 必须将 reviewTestCases 的 testCasesJson 作为参数传入 recordSuccessCase 的 testCasesJson 字段！
                   → 调用 recordSuccessCase 后任务完成，禁止继续任何操作
                10. 如果 reviewTestCases 返回 passed=false 或 score < 60：
                   → 分析 issues 中的具体问题，调整 generatorCode 重新生成（最多重试 2 次，不是3次！）
                   → 2 次后仍不通过 → 调用 recordFailureCase，并将当前最佳结果 JSON 放入 contextSummary

                【Early Exit 约束 - 最高优先级】
                11. reviewTestCases 成功后【必须立即】调用 recordSuccessCase，然后结束
                12. 不要在成功后继续尝试改进测试用例、不要生成更多用例、不要再次 review
                13. 如果连续 2 次 reviewTestCases 失败，立即调用 recordFailureCase 退出
                14. 【禁止直接输出 JSON】不要在消息正文中直接输出 JSON，必须通过 recordSuccessCase 或 recordFailureCase 工具提交
                15. 无论成功失败，最终都必须调用 recordSuccessCase 或 recordFailureCase

                【Generator 代码生成规范 - 最高优先级】
                16. generatorCode 和 solutionCode 都必须使用 `public class Main`，禁止使用其他类名！
                17. 禁止使用 TestGenerator、Generator、TestCaseGenerator 等其他类名
                18. 如果 generatorCode 需要额外的辅助类，只能作为内部类，不能独立声明
                19. 禁止 package 声明，禁止非 Main 类名的 public class
                20. 执行前必须确认代码中只有 `public class Main`，没有其他 public class

                【重要】所有代码必须使用 Java，类名必须是 Main，禁止 package 声明。
                executeCode 和 runGeneratorCode 的 language 参数必须为 "java"。
                """)
        String generateTestCases(
                @MemoryId String memoryId,
                @V("problem") ProblemGenerationResponse problem,
                @V("referenceSamplesJson") String referenceSamplesJson,
                @V("primarySolutionCode") String primarySolutionCode,
                @V("crossValidatorSolutionCode") String crossValidatorSolutionCode,
                @V("budgetWarning") String budgetWarning);
    }

    /**
     * 反转义 JSON 双重编码的字符串。
     * AI 返回的 solutionCode 可能在 JSON 中被双重转义，如 \\n -> 真实换行，\\t -> 真实 tab。
     */
    private String unescapeJsonEncodedString(String code) {
        if (code == null || code.isBlank()) return code;
        // AI JSON 双重编码：\\n -> 字面量\ + n -> 需要变成真实换行
        return code
                .replace("\\n", "\n")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }
}
