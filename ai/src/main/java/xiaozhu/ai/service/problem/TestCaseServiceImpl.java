package xiaozhu.ai.service.problem;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import xiaozhu.ai.config.TestCaseGenerationConfig;
import xiaozhu.ai.exception.AiErrorType;
import xiaozhu.ai.exception.AiGenerationException;
import xiaozhu.ai.memory.CaseSearchService;
import xiaozhu.ai.memory.FailureCase;
import xiaozhu.ai.metrics.AiMetricsService;
import xiaozhu.ai.model.SolutionCodeGenerationResponse;
import xiaozhu.ai.model.VerifiedTestCaseGenerationResponse;
import xiaozhu.ai.service.llm.*;
import xiaozhu.ai.util.ExtractJsonFromUtil;
import xiaozhu.common.constant.RedisKeyConstant;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.ProblemGenerationResponse.TestCase;
import xiaozhu.common.dto.SandboxExecuteRequest;
import xiaozhu.common.dto.SandboxExecuteResponse;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import xiaozhu.common.feign.JudgeSandboxFeignClient;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 测试用例生成服务实现类
 *
 * 使用沙箱验算模式生成测试用例：
 * AI 生成 solutionCode → 沙箱验证 → AI 生成 generatorCode → 沙箱执行得到 verified expectedOutput
 *
 * 特点：
 * - expectedOutput 完全由 solutionCode 执行产生，彻底消除 AI 幻觉
 * - 不再支持降级模式（模式B存在严重AI幻觉问题）
 * - 失败时抛出 AiGenerationException，由调用方决定是否重试
 */
@Slf4j
@Service
public class TestCaseServiceImpl implements TestCaseService {

    private static final long CACHE_TTL_DAYS = 7L;
    private static final int REFERENCE_CASE_LIMIT = 2;
    private static final String LANGUAGE_JAVA = "java";

    private final RedisTemplate<String, Object> redisTemplate;
    private final SolutionCodeGenerationAiService solutionCodeGenerationAiService;
    private final GeneratorCodeGenerationAiService generatorCodeGenerationAiService;
    private final AiMetricsService aiMetricsService;
    private final JudgeSandboxFeignClient judgeSandboxFeignClient;
    private final CaseSearchService caseSearchService;
    private final TestCaseGenerationConfig generationConfig;

    public TestCaseServiceImpl(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("testCaseChatModelPrototype") ChatModel testCaseChatModel,
            @Qualifier("testCaseChatModelV2Prototype") ChatModel testCaseChatModelV2,
            @Value("${ai.testcase.use-v2-model:false}") boolean useV2Model,
            AiMetricsService aiMetricsService,
            JudgeSandboxFeignClient judgeSandboxFeignClient,
            CaseSearchService caseSearchService,
            TestCaseGenerationConfig generationConfig) {
        this.redisTemplate = redisTemplate;
        this.aiMetricsService = aiMetricsService;
        this.judgeSandboxFeignClient = judgeSandboxFeignClient;
        this.caseSearchService = caseSearchService;
        this.generationConfig = generationConfig;
        ChatModel effectiveModel = useV2Model ? testCaseChatModelV2 : testCaseChatModel;
        log.info("[TestCaseServiceImpl] 初始化测试用例生成模型，useV2Model={}，实际使用={}",
                useV2Model, effectiveModel.getClass().getSimpleName());
        VerifiedTestCaseGenerationAiService verifiedTestCaseGenerationAiService = AiServices.builder(VerifiedTestCaseGenerationAiService.class)
                .chatModel(effectiveModel)
                .build();
        this.solutionCodeGenerationAiService = AiServices.builder(SolutionCodeGenerationAiService.class)
                .chatModel(effectiveModel)
                .build();
        this.generatorCodeGenerationAiService = AiServices.builder(GeneratorCodeGenerationAiService.class)
                .chatModel(effectiveModel)
                .build();
    }

    // ==================== 【新模式·沙箱验算】 ====================

    /**
     * 生成测试用例（沙箱验算模式）
     *
     * 流程：
     * 1. AI 生成 solutionCode（强制样例验证）
     * 2. 沙箱跑 solutionCode 验证样例通过
     * 3. AI 生成 generatorCode（基于已验证的 solutionCode）
     * 4. 沙箱跑 generatorCode → 获得多行 rawInput
     * 5. 沙箱跑 solutionCode 对每个 rawInput → 获得真实 expectedOutput
     * 6. 去重、构建最终响应
     *
     * expectedOutput 完全由 solutionCode 执行产生，彻底消除 AI 算法逻辑幻觉。
     *
     * @param problem 题目信息
     * @return verified 测试用例
     * @deprecated 已废弃，请使用 {@link xiaozhu.ai.agent.service.TestCaseGenerationAgentService#generate}
     */
    @Deprecated
    public TestCaseGenerationResponse generateVerifiedTestCases(ProblemGenerationResponse problem) {
        int maxRetries = generationConfig.getMaxRetries();
        String lastError = null;
        String lastSolutionCode = null;

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                log.info("[TestCaseServiceImpl] 第 {} 次尝试开始", attempt);
                return generateWithRetryContext(problem, attempt > 1 ? lastError : null, lastSolutionCode);
            } catch (AiGenerationException e) {
                lastError = buildErrorContext(e);
                lastSolutionCode = null;
                log.warn("[TestCaseServiceImpl] 第 {} 次尝试失败: {} - {}", attempt, e.getErrorType(), e.getMessage());

                // 记录失败案例
                recordFailureCase(problem, null, e.getErrorType().name(), e.getMessage());

                if (attempt < maxRetries) {
                    // 指数退避
                    try {
                        long sleepTime = generationConfig.calculateDelayMs(attempt);
                        log.info("[TestCaseServiceImpl] 等待 {}ms 后重试...", sleepTime);
                        Thread.sleep(sleepTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new AiGenerationException(AiErrorType.UNKNOWN_ERROR, "重试被中断");
                    }
                }
            }
        }

        // 所有重试都失败
        aiMetricsService.recordAiCallError(AiErrorType.AI_GENERATION_FAILED_AFTER_RETRY.name());
        throw new AiGenerationException(AiErrorType.AI_GENERATION_FAILED_AFTER_RETRY,
                "重试全部失败");
    }

    /**
     * 构建错误上下文摘要
     */
    private String buildErrorContext(AiGenerationException e) {
        return String.format("错误类型: %s, 错误信息: %s",
                e.getErrorType(), e.getMessage());
    }

    /**
     * 带重试上下文的生成逻辑
     */
    private TestCaseGenerationResponse generateWithRetryContext(
            ProblemGenerationResponse problem,
            String lastError,
            String lastSolutionCode) {
        long startTime = System.currentTimeMillis();
        String aiResponse1 = null;
        String aiResponse2 = null;
        String problemTitle = problem != null ? problem.getTitle() : "unknown";

        log.info("[TestCaseServiceImpl] 【沙箱验算模式】开始生成测试用例，题目={}", problemTitle);

        try {
            // Step 1：构建 AI 请求公共部分
            String problemDesc = buildProblemDescription(problem);
            String sampleCases = buildReferenceCaseJson(problem);
            log.info("[TestCaseServiceImpl] Step1 构建 AI 请求成功，题目描述长度={}, 示例用例长度={}",
                    problemDesc.length(), sampleCases.length());

            String problemPreview = problemDesc.length() > 200 ? problemDesc.substring(0, 200) + "..." : problemDesc;
            log.debug("[TestCaseServiceImpl] Step1 题目描述预览:\n{}", problemPreview);

            // ============ 阶段 A：AI 生成 solutionCode ============
            log.info("[TestCaseServiceImpl] ========== 阶段A 开始 ======== 题目={}", problemTitle);
            log.info("[TestCaseServiceImpl] 阶段A 开始调用 AI 生成 solutionCode...");

            // 构建重试上下文
            String retryContext = buildRetryContext(lastError, lastSolutionCode, problem);
            String instruction = "请严格按照提示词要求生成JSON对象，solutionCode必须是完整可运行的Java代码。" + retryContext;

            long aiStartTime = System.currentTimeMillis();
            aiResponse1 = solutionCodeGenerationAiService.generateSolutionCode(
                    problemDesc,
                    sampleCases,
                    instruction
            );
            long aiDuration = System.currentTimeMillis() - aiStartTime;
            log.info("[TestCaseServiceImpl] 阶段A AI 调用完成，耗时={}ms，响应长度={}", aiDuration,
                    aiResponse1 != null ? aiResponse1.length() : 0);

            if (StrUtil.isBlank(aiResponse1)) {
                log.error("[TestCaseServiceImpl] 阶段A AI 返回空响应");
                aiMetricsService.recordAiCallError(AiErrorType.AI_RESPONSE_EMPTY.name());
                throw new AiGenerationException(AiErrorType.AI_RESPONSE_EMPTY, "阶段A AI 返回空响应");
            }
            log.debug("[TestCaseServiceImpl] 阶段A AI 响应预览（前300字符）:\n{}",
                    aiResponse1.length() > 300 ? aiResponse1.substring(0, 300) + "..." : aiResponse1);

            String cleanJson = ExtractJsonFromUtil.extractJsonFromResponse(aiResponse1);
            log.info("[TestCaseServiceImpl] 阶段A JSON 提取结果，长度={}", cleanJson != null ? cleanJson.length() : 0);
            if (StrUtil.isBlank(cleanJson)) {
                log.error("[TestCaseServiceImpl] 阶段A 无法提取 JSON");
                aiMetricsService.recordAiCallError(AiErrorType.AI_JSON_EXTRACT_FAILED.name());
                throw new AiGenerationException(AiErrorType.AI_JSON_EXTRACT_FAILED, "阶段A 无法提取 JSON");
            }

            cleanJson = removeInvisibleCharacters(cleanJson);
            cleanJson = escapeNewlinesInJsonStrings(cleanJson);

            SolutionCodeGenerationResponse solutionResponse = JSON.parseObject(cleanJson, SolutionCodeGenerationResponse.class);
            if (solutionResponse == null || StrUtil.isBlank(solutionResponse.getSolutionCode())) {
                log.error("[TestCaseServiceImpl] 阶段A solutionCode 为空或解析失败");
                aiMetricsService.recordAiCallError(AiErrorType.AI_JSON_PARSE_FAILED.name());
                throw new AiGenerationException(AiErrorType.AI_JSON_PARSE_FAILED, "阶段A solutionCode 为空或解析失败");
            }
            log.info("[TestCaseServiceImpl] 阶段A solutionCode 解析成功，长度={}", solutionResponse.getSolutionCode().length());

            // 打印 solutionCode 前10行预览
            String[] solutionLines = solutionResponse.getSolutionCode().split("\n");
            String solutionPreview = solutionLines.length > 10
                ? String.join("\n", java.util.Arrays.copyOfRange(solutionLines, 0, 10)) + "\n..."
                : solutionResponse.getSolutionCode();
            log.info("[TestCaseServiceImpl] 阶段A solutionCode 预览（前10行）:\n{}", solutionPreview);

            // Step 2：沙箱验证 solutionCode
            log.info("[TestCaseServiceImpl] ========== Step2 开始 ======== 沙箱验证 solutionCode");
            String solutionCode = validateAndFixCode(solutionResponse.getSolutionCode());
            if (StrUtil.isBlank(solutionCode)) {
                log.error("[TestCaseServiceImpl] Step2 solutionCode 格式修复失败（返回空）");
                aiMetricsService.recordAiCallError(AiErrorType.SOLUTION_CODE_VALIDATION_FAILED.name());
                throw new AiGenerationException(AiErrorType.SOLUTION_CODE_VALIDATION_FAILED, "Step2 solutionCode 格式修复失败");
            }
            log.info("[TestCaseServiceImpl] Step2 solutionCode 格式修复完成，长度={}", solutionCode.length());

            List<ProblemGenerationResponse.TestCase> referenceCases =
                    (problem != null && problem.getTestCases() != null) ? problem.getTestCases() : Collections.emptyList();
            log.info("[TestCaseServiceImpl] Step2 参考样例数量={}", referenceCases.size());

            boolean solutionVerified = verifySolutionCodeWithSamples(solutionCode, referenceCases);
            if (!solutionVerified) {
                log.error("[TestCaseServiceImpl] Step2 solutionCode 样例验证未通过，算法逻辑可能有误");
                aiMetricsService.recordAiCallError(AiErrorType.SANDBOX_VERIFY_FAILED.name());
                throw new AiGenerationException(AiErrorType.SANDBOX_VERIFY_FAILED,
                        "Step2 solutionCode 样例验证未通过，算法逻辑可能有误",
                        String.format("题目=%s", problemTitle));
            }
            log.info("[TestCaseServiceImpl] Step2 solutionCode 样例验证全部通过！");

            // ============ 阶段 B：AI 生成 generatorCode ============
            log.info("[TestCaseServiceImpl] ========== 阶段B 开始 ======== AI 生成 generatorCode");
            log.info("[TestCaseServiceImpl] 阶段B 传入 solutionCode 预览（前5行）:\n{}",
                    solutionLines.length > 5
                        ? String.join("\n", java.util.Arrays.copyOfRange(solutionLines, 0, 5)) + "\n..."
                        : solutionResponse.getSolutionCode());

            long aiStartTime2 = System.currentTimeMillis();
            aiResponse2 = generatorCodeGenerationAiService.generateGeneratorCode(
                    problemDesc,
                    sampleCases,
                    solutionCode,
                    "请严格按照提示词要求生成JSON对象，generatorCode必须是完整可运行的Java代码。"
            );
            long aiDuration2 = System.currentTimeMillis() - aiStartTime2;
            log.info("[TestCaseServiceImpl] 阶段B AI 调用完成，耗时={}ms，响应长度={}", aiDuration2,
                    aiResponse2 != null ? aiResponse2.length() : 0);

            if (StrUtil.isBlank(aiResponse2)) {
                log.error("[TestCaseServiceImpl] 阶段B AI 返回空响应");
                aiMetricsService.recordAiCallError(AiErrorType.AI_RESPONSE_EMPTY.name());
                throw new AiGenerationException(AiErrorType.AI_RESPONSE_EMPTY, "阶段B AI 返回空响应");
            }
            log.debug("[TestCaseServiceImpl] 阶段B AI 响应预览（前300字符）:\n{}",
                    aiResponse2.length() > 300 ? aiResponse2.substring(0, 300) + "..." : aiResponse2);

            String cleanJson2 = ExtractJsonFromUtil.extractJsonFromResponse(aiResponse2);
            log.info("[TestCaseServiceImpl] 阶段B JSON 提取结果，长度={}", cleanJson2 != null ? cleanJson2.length() : 0);
            if (StrUtil.isBlank(cleanJson2)) {
                log.error("[TestCaseServiceImpl] 阶段B 无法提取 JSON");
                aiMetricsService.recordAiCallError(AiErrorType.AI_JSON_EXTRACT_FAILED.name());
                throw new AiGenerationException(AiErrorType.AI_JSON_EXTRACT_FAILED, "阶段B 无法提取 JSON");
            }

            cleanJson2 = removeInvisibleCharacters(cleanJson2);
            cleanJson2 = escapeNewlinesInJsonStrings(cleanJson2);

            VerifiedTestCaseGenerationResponse genResponse = JSON.parseObject(cleanJson2, VerifiedTestCaseGenerationResponse.class);
            if (genResponse == null || StrUtil.isBlank(genResponse.getGeneratorCode())) {
                log.error("[TestCaseServiceImpl] 阶段B generatorCode 为空或解析失败");
                aiMetricsService.recordAiCallError(AiErrorType.AI_JSON_PARSE_FAILED.name());
                throw new AiGenerationException(AiErrorType.AI_JSON_PARSE_FAILED, "阶段B generatorCode 为空或解析失败");
            }
            log.info("[TestCaseServiceImpl] 阶段B generatorCode 解析成功，长度={}, generatorArgs={}",
                    genResponse.getGeneratorCode().length(), genResponse.getGeneratorArgs());

            // 打印 generatorCode 前10行预览
            String[] genLines = genResponse.getGeneratorCode().split("\n");
            String genPreview = genLines.length > 10
                ? String.join("\n", java.util.Arrays.copyOfRange(genLines, 0, 10)) + "\n..."
                : genResponse.getGeneratorCode();
            log.info("[TestCaseServiceImpl] 阶段B generatorCode 预览（前10行）:\n{}", genPreview);

            // Step 3：沙箱运行 generatorCode 获取 rawInputs
            log.info("[TestCaseServiceImpl] ========== Step3 开始 ======== 沙箱运行生成器");
            String generatorArgs = StrUtil.blankToDefault(genResponse.getGeneratorArgs(), "42");
            String generatorCode = validateAndFixCode(genResponse.getGeneratorCode());
            if (StrUtil.isBlank(generatorCode)) {
                log.error("[TestCaseServiceImpl] Step3 generatorCode 格式修复失败（返回空）");
                aiMetricsService.recordAiCallError(AiErrorType.GENERATOR_CODE_VALIDATION_FAILED.name());
                throw new AiGenerationException(AiErrorType.GENERATOR_CODE_VALIDATION_FAILED, "Step3 generatorCode 格式修复失败");
            }
            log.info("[TestCaseServiceImpl] Step3 generatorCode 格式修复完成，长度={}, generatorArgs={}", generatorCode.length(), generatorArgs);

            String generatorOutput = runGeneratorAndGetOutput(generatorCode, generatorArgs);
            if (generatorOutput == null || generatorOutput.isBlank()) {
                log.error("[TestCaseServiceImpl] Step3 生成器执行失败或返回空输出");
                aiMetricsService.recordAiCallError(AiErrorType.SANDBOX_RUNTIME_ERROR.name());
                throw new AiGenerationException(AiErrorType.SANDBOX_RUNTIME_ERROR, "Step3 生成器执行失败或返回空输出");
            }
            log.info("[TestCaseServiceImpl] Step3 生成器执行成功，输出长度={}", generatorOutput.length());
            log.debug("[TestCaseServiceImpl] Step3 生成器原始输出预览（前500字符）:\n{}",
                    generatorOutput.length() > 500 ? generatorOutput.substring(0, 500) + "\n...(省略)" : generatorOutput);

            // 解析 ---INPUT--- 格式
            List<String> rawInputs = parseGeneratorOutput(generatorOutput);
            if (rawInputs.isEmpty()) {
                log.error("[TestCaseServiceImpl] Step3 无法解析生成器输出（解析结果为空）");
                aiMetricsService.recordAiCallError(AiErrorType.AI_JSON_EXTRACT_FAILED.name());
                throw new AiGenerationException(AiErrorType.AI_JSON_EXTRACT_FAILED, "Step3 无法解析生成器输出");
            }
            log.info("[TestCaseServiceImpl] Step3 解析成功，共 {} 个 rawInput", rawInputs.size());
            for (int i = 0; i < rawInputs.size(); i++) {
                String inputPreview = rawInputs.get(i).length() > 60
                    ? rawInputs.get(i).substring(0, 60) + "..." : rawInputs.get(i);
                log.debug("[TestCaseServiceImpl] Step3 rawInput#{} 预览: {}", i + 1, inputPreview);
            }

            // Step 4：用 solutionCode 跑每个 rawInput，获取真实 expectedOutput
            log.info("[TestCaseServiceImpl] ========== Step4 开始 ======== 沙箱跑 solutionCode 逐个验证 rawInput，共 {} 个", rawInputs.size());
            List<VerifiedCase> verifiedCases = new ArrayList<>();
            int failCount = 0;

            for (int i = 0; i < rawInputs.size(); i++) {
                String rawInput = rawInputs.get(i);
                if (rawInput == null || rawInput.isBlank()) {
                    log.warn("[TestCaseServiceImpl] Step4 第#{} 个 rawInput 为空，跳过", i + 1);
                    continue;
                }

                String expectedOutput = runSolutionAndGetOutput(solutionCode, rawInput);
                if (expectedOutput == null) {
                    log.warn("[TestCaseServiceImpl] Step4 第#{} 个 rawInput 执行失败（solutionCode 报错），跳过", i + 1);
                    log.debug("[TestCaseServiceImpl] Step4 第#{} 个 rawInput 内容: {}",
                            i + 1, rawInput.length() > 100 ? rawInput.substring(0, 100) + "..." : rawInput);
                    failCount++;
                    continue;
                }

                log.debug("[TestCaseServiceImpl] Step4 rawInput#{} 验证成功: input={} -> output={}",
                        i + 1,
                        rawInput.length() > 50 ? rawInput.substring(0, 50) + "..." : rawInput,
                        expectedOutput.length() > 50 ? expectedOutput.substring(0, 50) + "..." : expectedOutput);
                verifiedCases.add(new VerifiedCase(rawInput, expectedOutput));
            }

            log.info("[TestCaseServiceImpl] Step4 执行完成，成功 {} 个，失败 {} 个",
                    verifiedCases.size(), failCount);

            if (verifiedCases.isEmpty()) {
                log.error("[TestCaseServiceImpl] Step4 所有 rawInput 执行均失败（solutionCode 对所有输入都报错）");
                aiMetricsService.recordAiCallError(AiErrorType.SANDBOX_RUNTIME_ERROR.name());
                throw new AiGenerationException(AiErrorType.SANDBOX_RUNTIME_ERROR, "Step4 所有 rawInput 执行均失败");
            }

            // Step 5：去重（按 input 去重）
            log.info("[TestCaseServiceImpl] Step5 开始去重，原始数量={}", verifiedCases.size());
            List<VerifiedCase> dedupedCases = new ArrayList<>();
            Set<String> seenInputs = new java.util.HashSet<>();
            int dupRemoved = 0;
            for (VerifiedCase vc : verifiedCases) {
                if (seenInputs.add(vc.input())) {
                    dedupedCases.add(vc);
                } else {
                    dupRemoved++;
                    log.debug("[TestCaseServiceImpl] Step5 跳过重复用例: input={}",
                            vc.input().length() > 50 ? vc.input().substring(0, 50) + "..." : vc.input());
                }
            }
            log.info("[TestCaseServiceImpl] Step5 去重完成，有效用例={}，去重={}", dedupedCases.size(), dupRemoved);

            // Step 6：构建最终响应
            log.info("[TestCaseServiceImpl] Step6 开始构建 TestCaseGenerationResponse...");
            TestCaseGenerationResponse response = buildTestCaseGenerationResponse(dedupedCases);
            if (response.getTestCases() == null || response.getTestCases().isEmpty()) {
                log.error("[TestCaseServiceImpl] Step6 构建响应失败");
                throw new AiGenerationException(AiErrorType.UNKNOWN_ERROR, "Step6 构建响应失败");
            }
            log.info("[TestCaseServiceImpl] Step6 构建成功，共 {} 个用例", response.getTestCases().size());

            // 记录指标
            long totalDuration = System.currentTimeMillis() - startTime;
            aiMetricsService.recordTestCaseGeneration(totalDuration);
            // Token 使用量由 TokenUsageListener 通过 ChatModelListener 自动记录，无需手动处理

            log.info("[TestCaseServiceImpl] 【沙箱验算模式】生成完成！题目={}, 用例数量={}, 总耗时={}ms",
                    problemTitle, response.getTestCases().size(), totalDuration);
            return response;

        } catch (JSONException e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[TestCaseServiceImpl] 【沙箱验算模式】JSON 解析失败，耗时={}ms", duration, e);
            String rawResp = aiResponse1 != null ? aiResponse1 : aiResponse2;
            if (rawResp != null) {
                log.error("[TestCaseServiceImpl] 原始响应前500字符: {}",
                        rawResp.substring(0, Math.min(500, rawResp.length())));
            }
            aiMetricsService.recordAiCallError(AiErrorType.AI_JSON_PARSE_FAILED.name());
            throw new AiGenerationException(AiErrorType.AI_JSON_PARSE_FAILED, "【沙箱验算模式】JSON 解析失败", e.getMessage());
        } catch (AiGenerationException e) {
            // 已经是业务异常，重新抛出，记录已由调用处处理
            throw e;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[TestCaseServiceImpl] 【沙箱验算模式】生成失败，耗时={}ms", duration, e);
            aiMetricsService.recordAiCallError(AiErrorType.UNKNOWN_ERROR.name());
            throw new AiGenerationException(AiErrorType.UNKNOWN_ERROR, "【沙箱验算模式】生成失败: " + e.getMessage());
        }
    }

    /**
     * 用 sample_cases 验证 solutionCode 正确性（沙箱执行）
     */
    private boolean verifySolutionCodeWithSamples(String solutionCode,
                                                  List<ProblemGenerationResponse.TestCase> referenceCases) {
        if (referenceCases.isEmpty()) {
            log.warn("[TestCaseServiceImpl] 【验证】无参考样例，跳过样例验证，直接通过");
            return true;
        }

        log.info("[TestCaseServiceImpl] 【验证】开始验证，共 {} 个参考样例", referenceCases.size());

        for (int idx = 0; idx < referenceCases.size(); idx++) {
            ProblemGenerationResponse.TestCase sample = referenceCases.get(idx);
            if (sample == null || StrUtil.isBlank(sample.getInput()) || StrUtil.isBlank(sample.getExpectedOutput())) {
                log.debug("[TestCaseServiceImpl] 【验证】样例#{} 跳过（空 input 或 expectedOutput）", idx + 1);
                continue;
            }

            String expectedForLog = sample.getExpectedOutput().length() > 80
                    ? sample.getExpectedOutput().substring(0, 80) + "..." : sample.getExpectedOutput();
            log.info("[TestCaseServiceImpl] 【验证】样例#{} 开始验证", idx + 1);

            String actualOutput = runSolutionAndGetOutput(solutionCode, sample.getInput());
            if (actualOutput == null) {
                log.error("[TestCaseServiceImpl] 【验证】样例#{} 执行失败（编译错误或运行时异常）", idx + 1);
                return false;
            }

            String actualTrim = actualOutput.trim();
            String expectedTrim = sample.getExpectedOutput().trim();
            if (!actualTrim.equals(expectedTrim)) {
                String actualForLog = actualOutput.length() > 80
                        ? actualOutput.substring(0, 80) + "..." : actualOutput;
                log.error("[TestCaseServiceImpl] 【验证】样例#{} 输出不匹配！", idx + 1);
                log.error("[TestCaseServiceImpl] 【验证】样例#{} 期望输出: {}", idx + 1, expectedForLog);
                log.error("[TestCaseServiceImpl] 【验证】样例#{} 实际输出: {}", idx + 1, actualForLog);
                log.error("[TestCaseServiceImpl] 【验证】差异: 期望长度={}, 实际长度={}", expectedTrim.length(), actualTrim.length());
                return false;
            }
            log.info("[TestCaseServiceImpl] 【验证】样例#{} 通过 ✓", idx + 1);
        }

        log.info("[TestCaseServiceImpl] 【验证】全部 {} 个样例验证通过！", referenceCases.size());
        return true;
    }

    /**
     * 沙箱运行生成器，获取 rawInput 列表
     */
    private String runGeneratorAndGetOutput(String generatorCode, String generatorArgs) {
        try {
            SandboxExecuteRequest request = new SandboxExecuteRequest();
            request.setCode(generatorCode);
            request.setLanguage(LANGUAGE_JAVA);
            request.setUserInput(generatorArgs);

            long callStartTime = System.currentTimeMillis();
            SandboxExecuteResponse response = judgeSandboxFeignClient.runCode(request);
            long callDuration = System.currentTimeMillis() - callStartTime;
            log.info("[TestCaseServiceImpl] 【沙箱调用-gen】请求已发送，代码长度={}, 耗时={}ms",
                    generatorCode.length(), callDuration);

            if (response == null) {
                log.error("[TestCaseServiceImpl] 【沙箱调用-gen】响应为 null");
                return null;
            }

            log.info("[TestCaseServiceImpl] 【沙箱调用-gen】返回，success={}, exitCode={}",
                    response.isSuccess(), response.getExitCode());

            if (!response.isSuccess()) {
                String errors = response.getErrorMessages() != null
                        ? String.join("\n", response.getErrorMessages()) : "unknown";
                log.error("[TestCaseServiceImpl] 【沙箱调用-gen】执行失败！exitCode={}", response.getExitCode());
                log.error("[TestCaseServiceImpl] 【沙箱调用-gen】错误信息: {}", errors);
                return null;
            }

            List<String> outputs = response.getRawOutputList();
            if (outputs == null || outputs.isEmpty()) {
                log.warn("[TestCaseServiceImpl] 【沙箱调用-gen】输出列表为空");
                return null;
            }

            String rawOutput = outputs.get(0);
            log.info("[TestCaseServiceImpl] 【沙箱调用-gen】成功，输出长度={}", rawOutput.length());
            log.debug("[TestCaseServiceImpl] 【沙箱调用-gen】原始输出内容:\n{}", rawOutput);
            return rawOutput;

        } catch (Exception e) {
            log.error("[TestCaseServiceImpl] 【沙箱调用-gen】调用异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 解析生成器输出，提取 rawInput 列表
     * 格式：---INPUT---\n[完整输入]\n---INPUT---\n[完整输入]...
     */
    private List<String> parseGeneratorOutput(String rawOutput) {
        List<String> inputs = new ArrayList<>();
        String[] parts = rawOutput.split("---INPUT---");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                inputs.add(trimmed);
            }
        }
        log.info("[TestCaseServiceImpl] 【解析输出】解析到 {} 个 rawInput", inputs.size());
        return inputs;
    }

    /**
     * 沙箱运行 solutionCode，获取单个输入的真实输出
     */
    private String runSolutionAndGetOutput(String solutionCode, String userInput) {
        try {
            SandboxExecuteRequest request = new SandboxExecuteRequest();
            request.setCode(solutionCode);
            request.setLanguage(LANGUAGE_JAVA);
            request.setUserInput(userInput);

            long callStartTime = System.currentTimeMillis();
            SandboxExecuteResponse response = judgeSandboxFeignClient.runCode(request);
            long callDuration = System.currentTimeMillis() - callStartTime;

            if (response == null) {
                log.warn("[TestCaseServiceImpl] 【沙箱调用-sol】响应为 null，耗时={}ms", callDuration);
                return null;
            }

            if (!response.isSuccess()) {
                String errors = response.getErrorMessages() != null
                        ? String.join("\n", response.getErrorMessages()) : "unknown";
                log.warn("[TestCaseServiceImpl] 【沙箱调用-sol】执行失败！exitCode={}, 耗时={}ms, errors={}",
                        response.getExitCode(), callDuration, errors);
                return null;
            }

            List<String> outputs = response.getRawOutputList();
            if (outputs == null || outputs.isEmpty()) {
                log.warn("[TestCaseServiceImpl] 【沙箱调用-sol】输出为空，耗时={}ms", callDuration);
                return null;
            }

            String rawOutput = outputs.get(0);
            log.debug("[TestCaseServiceImpl] 【沙箱调用-sol】成功，耗时={}ms, 输出长度={}", callDuration, rawOutput.length());
            return rawOutput;

        } catch (Exception e) {
            log.warn("[TestCaseServiceImpl] 【沙箱调用-sol】调用异常: {}", e.getMessage());
            return null;
        }
    }

    private record VerifiedCase(String input, String expectedOutput) {}

    /**
     * 根据 verified 用例构建 TestCaseGenerationResponse
     */
    private TestCaseGenerationResponse buildTestCaseGenerationResponse(List<VerifiedCase> cases) {
        TestCaseGenerationResponse response = new TestCaseGenerationResponse();
        List<TestCaseGenerationResponse.TestCaseDetail> details = new ArrayList<>();

        for (int i = 0; i < cases.size(); i++) {
            VerifiedCase vc = cases.get(i);
            TestCaseGenerationResponse.TestCaseDetail detail = new TestCaseGenerationResponse.TestCaseDetail();
            detail.setCaseIndex(i + 1);
            detail.setInput(vc.input());
            detail.setExpectedOutput(vc.expectedOutput());

            detail.setWeight(1.00);
            detail.setGenerationSource("CHECKER_VERIFIED");
            detail.setVersion(1);

            String caseContent = vc.input() + vc.expectedOutput();
            detail.setContentHash(DigestUtil.sha256Hex(caseContent));

            // 前3个为 SAMPLE（公开），其余为 HIDDEN
            if (i < 3) {
                detail.setIsPublic(1);
                detail.setCaseType("SAMPLE");
            } else {
                detail.setIsPublic(0);
                detail.setCaseType("HIDDEN");
            }

            details.add(detail);
        }

        response.setTestCases(details);
        return response;
    }

    // ==================== 其他辅助方法 ====================

    @Override
    public void saveTestCases(String userKey, String contentHash, TestCaseGenerationResponse response) {
        String testCaseRedisKey = buildTestCaseRedisKey(userKey, contentHash);
        log.info("[TestCaseServiceImpl] 保存测试用例到 Redis，key={}, count={}", testCaseRedisKey, response.getTestCases().size());
        redisTemplate.opsForValue().set(testCaseRedisKey, response, CACHE_TTL_DAYS, TimeUnit.DAYS);
        log.info("[TestCaseServiceImpl] Redis 保存成功，TTL={}天", CACHE_TTL_DAYS);
    }

    @Override
    public boolean testCasesExist(String userKey, String contentHash) {
        String testCaseRedisKey = buildTestCaseRedisKey(userKey, contentHash);
        boolean exists = Boolean.TRUE.equals(redisTemplate.hasKey(testCaseRedisKey));
        log.info("[TestCaseServiceImpl] 检查测试用例是否存在，key={}, exists={}", testCaseRedisKey, exists);
        return exists;
    }

    private String buildTestCaseRedisKey(String userKey, String contentHash) {
        return RedisKeyConstant.QUESTION_TEST_CASE_PREFIX + contentHash;
    }

    private String buildProblemDescription(ProblemGenerationResponse problem) {
        StringBuilder builder = new StringBuilder();
        if (problem != null) {
            if (StrUtil.isNotBlank(problem.getTitle())) {
                builder.append("【题目】").append(problem.getTitle()).append("\n");
            }
            if (StrUtil.isNotBlank(problem.getDescription())) {
                builder.append(problem.getDescription()).append("\n");
            }
            if (StrUtil.isNotBlank(problem.getInputDesc())) {
                builder.append("【输入格式】").append(problem.getInputDesc()).append("\n");
            }
            if (StrUtil.isNotBlank(problem.getOutputDesc())) {
                builder.append("【输出格式】").append(problem.getOutputDesc()).append("\n");
            }
        }
        if (builder.isEmpty()) {
            return "暂无题目描述";
        }
        return builder.toString();
    }

    private String buildReferenceCaseJson(ProblemGenerationResponse problem) {
        List<TestCase> source = problem != null && problem.getTestCases() != null
                ? problem.getTestCases()
                : Collections.emptyList();
        if (source.isEmpty()) {
            return "[]";
        }

        List<TestCase> ordered = new ArrayList<>();
        source.stream().filter(this::isPublicCase).forEach(ordered::add);
        source.stream().filter(testCase -> !isPublicCase(testCase)).forEach(ordered::add);

        List<LinkedHashMap<String, Object>> simplified = new ArrayList<>();
        int fallbackIndex = 1;
        for (TestCase testCase : ordered) {
            LinkedHashMap<String, Object> map = new LinkedHashMap<>();
            map.put("caseIndex", Optional.ofNullable(testCase.getCaseIndex()).orElse(fallbackIndex));
            map.put("input", StrUtil.blankToDefault(testCase.getInput(), ""));
            map.put("expectedOutput", StrUtil.blankToDefault(testCase.getExpectedOutput(), ""));
            map.put("isPublic", Optional.ofNullable(testCase.getIsPublic()).orElse(0));
            simplified.add(map);
            fallbackIndex++;
            if (simplified.size() >= REFERENCE_CASE_LIMIT) break;
        }
        return JSON.toJSONString(simplified);
    }

    private boolean isPublicCase(TestCase testCase) {
        return testCase != null && Objects.equals(testCase.getIsPublic(), 1);
    }

    /**
     * 从 JSON 字符串值中将真实换行替换为转义序列 \n
     *
     * AI 生成的 JSON 中，字符串值内的真实换行（如代码里的 \n）有时未被正确转义，
     * 直接放入 JSON 会破坏其结构。fastjson2 解析后，这些换行会出现在 Java String 中，
     * 导致代码源码变成非法。
     *
     * 此方法在 JSON.parseObject 之前预处理：把字符串值里的真实 \n \r 替换为 \\n，
     * 这样解析后 Java String 里只有两个字符 \ 和 n，是合法的 Java 源码。
     *
     * 逐字符状态机扫描：遇到 \" 则 toggle 字符串状态，字符串内遇到真实换行则替换。
     */
    private String escapeNewlinesInJsonStrings(String json) {
        if (json == null) return json;
        if (!json.contains("\n") && !json.contains("\r")) return json;

        StringBuilder sb = new StringBuilder();
        boolean inString = false;
        int count = 0;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);

            if (!inString) {
                if (c == '"') {
                    inString = true;
                    sb.append(c);
                } else if (c == '\\') {
                    // 跳过转义序列的下一个字符（\" \\ \/ \b \f \n \r \t \XXXX）
                    sb.append(c);
                    i++;
                    if (i < json.length()) {
                        sb.append(json.charAt(i));
                    }
                } else {
                    sb.append(c);
                }
            } else {
                // 字符串区域内
                if (c == '\\') {
                    // 检查是否是行续接符（\ + 真实换行）
                    int nextIndex = i + 1;
                    if (nextIndex < json.length()) {
                        char next = json.charAt(nextIndex);
                        if (next == '\n' || next == '\r') {
                            // 行续接符：\ 不输出，换行输出为 \\n（JSON 中合法的 \n 字符串）
                            i = nextIndex;
                            count++;
                            sb.append("\\n");
                            continue;
                        }
                    }
                    // 普通转义序列：保留 \，跳过下一个字符
                    sb.append(c);
                    i++;
                    if (i < json.length()) sb.append(json.charAt(i));
                } else if (c == '"') {
                    inString = false;
                    sb.append(c);
                } else if (c == '\n') {
                    count++;
                    sb.append("\\n");
                } else if (c == '\r') {
                    count++;
                    sb.append("\\r");
                } else {
                    sb.append(c);
                }
            }
        }

        if (count > 0) {
            log.info("[TestCaseServiceImpl] JSON 字符串中修复了 {} 处真实换行", count);
        }
        return sb.toString();
    }

    private String removeInvisibleCharacters(String source) {
        if (StrUtil.isBlank(source)) return source;
        return source
                .replaceAll("[\u200B-\u200D\uFEFF]", "")
                .replaceAll("[\u0000-\u0008\u000B\u000C\u000E-\u001F]", "");
    }

    /**
     * 验证并修复对数器代码格式
     *
     * 常见问题：
     * 1. \n 是字符串字面量 → 转为真实换行符
     * 2. 有 package 声明 → 移除
     * 3. import 在类之后 → 移到类之前
     * 4. 类名不是 Main → 替换为 Main
     * 5. 结尾有 \r 字符 → 移除
     *
     * @param code 原始代码
     * @return 修复后的代码，若无法修复则返回 null
     */
    private String validateAndFixCode(String code) {
        if (StrUtil.isBlank(code)) {
            return null;
        }

        log.info("[TestCaseServiceImpl] Step2.1 开始验证并修复代码格式...");
        log.info("[TestCaseServiceImpl] Step2.1 原始代码长度={}", code.length());

        String fixedCode = code;
        boolean modified = false;

        // 0. 移除所有不可见字符，统一换行符
        String originalCode = fixedCode;
        fixedCode = fixedCode
                .replaceAll("[\uFEFF\u200B\u200C\u200D\u2060]", "")  // BOM、零宽字符
                .replaceAll("[\u0000-\u0008\u000B\u000C\u000E-\u001F]", "")  // 控制字符
                .replaceAll("\r\n", "\n")
                .replaceAll("\r", "\n")
                .strip();
        if (!fixedCode.equals(originalCode)) {
            log.warn("[TestCaseServiceImpl] Step2.1 移除了隐藏字符");
            modified = true;
        }

        // 打印清理后的前10行
        String[] lines10 = fixedCode.split("\n", -1);
        log.info("[TestCaseServiceImpl] Step2.1 清理后前10行:");
        for (int i = 0; i < Math.min(10, lines10.length); i++) {
            log.info("[TestCaseServiceImpl] Step2.1   行{}: [{}]", i + 1, lines10[i]);
        }

        // 1. 核心：将字符串中的 \\n 替换为真实换行符
        // AI 在 JSON 中可能用 \\n 表示真实换行（用于跨行生成逻辑）
        if (fixedCode.contains("\\n")) {
            int count = countOccurrences(fixedCode, "\\n");
            log.info("[TestCaseServiceImpl] Step2.1 检测到 {} 处 \\n 转义序列，开始反转义...", count);
            fixedCode = fixedCode.replace("\\n", "\n");
            modified = true;
            log.info("[TestCaseServiceImpl] Step2.1 \\n 反转义完成，代码变为 {} 行",
                    fixedCode.split("\n", -1).length);
        }

        // 1.5 【关键修复】将字符串内部出现的真实换行恢复为 \n 转义
        // 原因：AI 有时生成 out.append("...\n...") 其中 \n 已经是真实换行了
        // 字符串字面量中不能有未转义的换行符，必须是 "..." + "\\n" + "..."
        // 注意：此处先做一次基础修复，后续所有转义序列处理完后还会在末尾再做一次
        String afterStringFix = fixNewlinesInStrings(fixedCode);
        if (!afterStringFix.equals(fixedCode)) {
            log.info("[TestCaseServiceImpl] Step2.1 检测到字符串内部存在真实换行，已转义修复");
            modified = true;
        }
        fixedCode = afterStringFix;

        // 2. 处理其他转义序列
        if (fixedCode.contains("\\t") || fixedCode.contains("\\\"") || fixedCode.contains("\\\\")) {
            log.info("[TestCaseServiceImpl] Step2.1 检测到其他转义序列");
            fixedCode = fixedCode.replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
            modified = true;
        }

        // 3. 移除 package 声明
        if (fixedCode.contains("package ")) {
            log.warn("[TestCaseServiceImpl] Step2.1 检测到 package 声明，将被移除");
            fixedCode = fixedCode.replaceAll("(?m)^\\s*package\\s+[\\w.]+\\s*;\\s*\\n?", "");
            modified = true;
        }

        // 4. 重组代码结构：import 在前，类定义在后
        StringBuilder imports = new StringBuilder();
        StringBuilder rest = new StringBuilder();
        boolean foundMainClass = false;
        String[] allLines = fixedCode.split("\n", -1);

        for (String line : allLines) {
            String trimmed = line.trim();
            if (!foundMainClass) {
                if (trimmed.startsWith("import ")) {
                    imports.append(line).append("\n");
                } else if (trimmed.startsWith("public class Main")) {
                    foundMainClass = true;
                    rest.append(line).append("\n");
                } else if (trimmed.startsWith("public class ") || trimmed.startsWith("class ")) {
                    // 替换类名为 Main
                    String fixedLine = line.replaceFirst("((public\\s+)?class\\s+)\\w+", "$1Main");
                    log.warn("[TestCaseServiceImpl] Step2.1 替换类名为 Main: {}", trimmed);
                    foundMainClass = true;
                    rest.append(fixedLine).append("\n");
                    modified = true;
                } else if (!trimmed.isEmpty()) {
                    // 其他内容（注释、空行等），放到后面
                    rest.append(line).append("\n");
                }
            } else {
                rest.append(line).append("\n");
            }
        }

        // 组装
        String restructured = rest.toString();
        if (!imports.isEmpty()) {
            int importCount = imports.toString().split("\n", -1).length;
            log.info("[TestCaseServiceImpl] Step2.1 提取到 {} 个 import 语句，将移到类之前", importCount);
            restructured = imports.toString() + rest.toString();
            modified = true;
        }

        // 移除末尾空行和多余空白
        restructured = restructured.trim();
        // 移除每行末尾的空白字符（包括 \r）
        restructured = restructured.replaceAll("(?m)\\s+$", "");

        if (!restructured.contains("public class Main")) {
            log.error("[TestCaseServiceImpl] Step2.1 修复后无法找到 'public class Main'");
            return null;
        }

        if (restructured.contains("package ")) {
            log.error("[TestCaseServiceImpl] Step2.1 修复后仍包含 package 声明");
            return null;
        }

        fixedCode = restructured;

        // 【最终兜底】所有转义序列处理完后，再做一次字符串内换行修复
        // 防止前面步骤（如 \\" → "）把 fixNewlinesInStrings 的结果改回去
        String finalFix = fixNewlinesInStrings(fixedCode);
        if (!finalFix.equals(fixedCode)) {
            log.info("[TestCaseServiceImpl] Step2.1 最终兜底：检测到字符串内部仍存在真实换行，已再次修复");
            modified = true;
        }
        fixedCode = finalFix;

        if (modified) {
            log.info("[TestCaseServiceImpl] Step2.1 代码格式已修复，最终行数={}", fixedCode.split("\n", -1).length);
            log.info("[TestCaseServiceImpl] Step2.1 修复后前10行:");
            String[] finalLines = fixedCode.split("\n", -1);
            for (int i = 0; i < Math.min(10, finalLines.length); i++) {
                log.info("[TestCaseServiceImpl] Step2.1   行{}: [{}]", i + 1, finalLines[i]);
            }
        } else {
            log.info("[TestCaseServiceImpl] Step2.1 代码格式正确，无需修改");
        }

        return fixedCode;
    }

    private int countOccurrences(String str, String sub) {
        if (str == null || sub == null || sub.isEmpty()) return 0;
        int count = 0, idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) { count++; idx += sub.length(); }
        return count;
    }

    /**
     * 修复字符串字面量内部的真实换行符，替换为 \n 转义序列
     *
     * AI 生成代码时可能出现以下非法形式（真实换行混在字符串中间）：
     *
     * Case A（行末 dangling close）：
     *     out.append("---CASE---
     * ");
     * Case B（行首 dangling open）：
     *         ").append("\n");
     *     ", "\
     *
     * 逐字符扫描，模拟 Java 编译器对字符串的处理逻辑：
     * 遇到 " → 切换字符串状态（若当前在字符串外则进入，在字符串内则退出）
     * 遇到 \ → 跳过下一个字符（转义序列）
     * 字符串内遇到真实换行 → 替换为 \n
     */
    private String fixNewlinesInStrings(String code) {
        if (code == null) return code;
        StringBuilder sb = new StringBuilder();
        boolean inString = false;

        for (int i = 0; i < code.length(); i++) {
            char c = code.charAt(i);

            if (!inString) {
                // 非字符串区域
                if (c == '"') {
                    inString = true;
                    sb.append(c);
                } else if (c == '\r') {
                    // 跳过 \r（兼容 Windows CRLF）
                    continue;
                } else {
                    sb.append(c);
                }
            } else {
                // 字符串区域内
                if (c == '\\') {
                    // 检查是否是行续接符（\ + 真实换行）
                    int nextIndex = i + 1;
                    if (nextIndex < code.length()) {
                        char next = code.charAt(nextIndex);
                        if (next == '\n' || next == '\r') {
                            // 行续接符：\ 不输出，换行输出为 \n
                            i = nextIndex;
                            sb.append("\\n");
                            continue;
                        }
                    }
                    // 普通转义序列：保留 \，跳过下一个字符
                    sb.append(c);
                    i++;
                    if (i < code.length()) sb.append(code.charAt(i));
                } else if (c == '"') {
                    // 字符串结束
                    inString = false;
                    sb.append(c);
                } else if (c == '\n' || c == '\r') {
                    // 【修复】字符串内部出现真实换行 → \n
                    if (c == '\r' && i + 1 < code.length() && code.charAt(i + 1) == '\n') {
                        // CRLF：跳过 \r，\n 触发修复
                        i++; // skip \r
                    }
                    sb.append("\\n");
                } else {
                    sb.append(c);
                }
            }
        }

        // 如果扫描完还在字符串内（文件末尾字符串未闭合）
        // 说明最后有多余的真实换行
        if (inString) {
            sb.append("\\n\"");
        }

        return sb.toString();
    }

    // ==================== 重试和失败记录相关方法 ====================

    /**
     * 构建重试上下文，用于传递给 AI
     */
    private String buildRetryContext(String lastError, String lastSolutionCode, ProblemGenerationResponse problem) {
        if (lastError == null || lastError.isBlank()) {
            return "";
        }

        StringBuilder context = new StringBuilder();
        context.append("\n\n【上次失败原因，请务必避免】\n");
        context.append(lastError).append("\n");

        if (lastSolutionCode != null && !lastSolutionCode.isBlank()) {
            context.append("\n上次尝试的 solutionCode 思路：\n");
            // 截取前200字符作为摘要
            String codePreview = lastSolutionCode.length() > 200
                    ? lastSolutionCode.substring(0, 200) + "..."
                    : lastSolutionCode;
            context.append(codePreview).append("\n");
            context.append("请换一个完全不同的算法思路来解决这道题。\n");
        }

        context.append("\n【重要】请确保：\n");
        context.append("1. 仔细理解题目描述，特别是关于'选择'、'跳过'、'连续'等操作的描述\n");
        context.append("2. 在生成 solutionCode 后，用样例进行验证\n");
        context.append("3. 如果验证失败，请重新分析题目并修正代码\n");

        return context.toString();
    }

    /**
     * 记录失败案例到记忆系统
     */
    private void recordFailureCase(ProblemGenerationResponse problem,
                                   String solutionCode,
                                   String errorType,
                                   String errorDetail) {
        if (caseSearchService == null) {
            log.warn("[TestCaseServiceImpl] CaseSearchService 未注入，跳过失败记录");
            return;
        }

        try {
            FailureCase failureCase = new FailureCase();
            failureCase.setProblemType(analyzeProblemType(problem));
            failureCase.setFailureReason(errorType);
            failureCase.setFailureDetail(errorDetail);
            failureCase.setProblemHash(problem != null
                    ? DigestUtil.sha256Hex((problem.getTitle() != null ? problem.getTitle() : "")
                    + (problem.getDescription() != null ? problem.getDescription() : ""))
                    : null);
            failureCase.setProblemTitle(problem != null ? problem.getTitle() : null);
            failureCase.setRetryCount(3);
            failureCase.setFinalErrorType(errorType);
            failureCase.setModelName("deepseek-chat");
            failureCase.setCreatedAt(LocalDateTime.now());

            caseSearchService.recordFailureCase(failureCase);
            log.info("[TestCaseServiceImpl] 失败案例已记录到记忆系统");
        } catch (Exception e) {
            log.warn("[TestCaseServiceImpl] 记录失败案例异常: {}", e.getMessage());
        }
    }

    /**
     * 分析题目类型
     */
    private String analyzeProblemType(ProblemGenerationResponse problem) {
        if (problem == null) {
            return "UNKNOWN";
        }

        // 基于标签判断题目类型
        StringBuilder typeBuilder = new StringBuilder();
        if (problem.getTagIds() != null) {
            for (Integer tagId : problem.getTagIds()) {
                if (tagId == 17) typeBuilder.append("DP,");  // 动态规划
                else if (tagId == 18) typeBuilder.append("GREEDY,");  // 贪心
                else if (tagId == 19) typeBuilder.append("BACKTRACK,");  // 回溯
                else if (tagId == 25) typeBuilder.append("TWO_POINTERS,");  // 双指针
                else if (tagId == 26) typeBuilder.append("SLIDING_WINDOW,");  // 滑动窗口
            }
        }

        if (typeBuilder.length() > 0) {
            return typeBuilder.substring(0, typeBuilder.length() - 1);
        }

        return "UNKNOWN";
    }
}
