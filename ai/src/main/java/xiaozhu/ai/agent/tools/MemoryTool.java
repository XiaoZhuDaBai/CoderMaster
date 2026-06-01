package xiaozhu.ai.agent.tools;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.alibaba.fastjson2.JSON;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xiaozhu.ai.agent.reviewer.ReviewResult;
import xiaozhu.ai.agent.reviewer.impl.TestCaseReviewer;
import xiaozhu.ai.memory.CaseSearchService;
import xiaozhu.ai.memory.FailureCase;
import xiaozhu.ai.memory.SuccessCase;
import xiaozhu.ai.model.TestCaseGenerationResponse;
import xiaozhu.common.dto.ProblemGenerationResponse;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Agent 记忆工具
 *
 * 提供给 Agent 调用的记忆相关工具：
 * 1. recordSuccessCase - 记录成功案例
 * 2. recordFailureCase - 记录失败案例
 * 3. reviewTestCases - 评审测试用例
 *
 * 自动提交兜底机制：
 * 当 reviewTestCases 返回 passed=true 且 score>=60 时，自动保存测试用例上下文。
 * 如果 Agent 最终没有调用 recordSuccessCase，TestCaseGenerationAgentService 会自动补调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MemoryTool {

    private final CaseSearchService caseSearchService;
    private final TestCaseReviewer testCaseReviewer;

    // 存储最后一次成功的测试用例（用于自动提交兜底）
    private static final AtomicReference<AutoSubmitContext> autoSubmitContext = new AtomicReference<>();

    /**
     * 自动提交上下文
     */
    public record AutoSubmitContext(
            String testCasesJson,
            String problemType,
            String algorithmKeyword,
            String problemHash,
            String problemTitle,
            String solutionCode,
            long generationDurationMs,
            int tokenUsed,
            String modelName
    ) {}

    /**
     * 记录成功案例
     *
     * 注意：该方法在成功后返回 TestCaseGenerationResponse JSON 格式，以便 Agent 正确结束。
     * 如果 Agent 调用此方法成功，任务即完成。
     */
    @Tool(name = "recordSuccessCase")
    public String recordSuccessCase(
            @P("problemType") String problemType,
            @P("algorithmKeyword") String algorithmKeyword,
            @P("generationStrategy") String generationStrategy,
            @P("testcaseCount") int testcaseCount,
            @P("successRate") double successRate,
            @P("problemHash") String problemHash,
            @P("problemTitle") String problemTitle,
            @P("solutionCode") String solutionCode,
            @P("contextSummary") String contextSummary,
            @P("generationDurationMs") long generationDurationMs,
            @P("tokenUsed") int tokenUsed,
            @P("modelName") String modelName,
            @P(value = "testCasesJson") String testCasesJson) {

        log.info("[MemoryTool] 记录成功案例，problemType={}, testcaseCount={}", problemType, testcaseCount);

        try {
            SuccessCase successCase = new SuccessCase();
            successCase.setProblemType(problemType);
            successCase.setAlgorithmKeyword(algorithmKeyword);
            successCase.setGenerationStrategy(generationStrategy);
            successCase.setTestcaseCount(testcaseCount);
            successCase.setSuccessRate(BigDecimal.valueOf(successRate));
            successCase.setProblemHash(problemHash);
            successCase.setProblemTitle(problemTitle);
            successCase.setSolutionCodeHash(solutionCode != null ? DigestUtil.sha256Hex(solutionCode) : null);
            successCase.setContextSummary(contextSummary);
            successCase.setGenerationDurationMs((int) generationDurationMs);
            successCase.setTokenUsed(tokenUsed);
            successCase.setModelName(modelName);
            successCase.setCreatedAt(LocalDateTime.now());

            caseSearchService.recordSuccessCase(successCase);
            log.info("[MemoryTool] 成功案例记录完成");

            // 注意：不清除 autoSubmitContext，让 tryAutoSubmit() 能正常执行

            // 返回 TestCaseGenerationResponse JSON 格式
            TestCaseGenerationResponse response = buildTestCaseResponse(testCasesJson, problemType,
                    algorithmKeyword, problemHash, problemTitle);
            String responseJson = JSON.toJSONString(response);
            log.info("[MemoryTool] 返回 TestCaseGenerationResponse JSON，长度={}", responseJson.length());
            return responseJson;
        } catch (Exception e) {
            log.error("[MemoryTool] 记录成功案例异常: {}", e.getMessage(), e);
            return JSON.toJSONString(new RecordResult(false, null, e.getMessage()));
        }
    }

    /**
     * 构建 TestCaseGenerationResponse
     */
    private TestCaseGenerationResponse buildTestCaseResponse(String testCasesJson, String problemType,
            String algorithmKeyword, String problemHash, String problemTitle) {
        TestCaseGenerationResponse response = new TestCaseGenerationResponse();

        // 解析测试用例
        List<TestCaseGenerationResponse.TestCaseDetail> testCases = new ArrayList<>();

        if (testCasesJson != null && !testCasesJson.isBlank()) {
            String jsonToParse = testCasesJson.trim();
            if (!jsonToParse.startsWith("[")) {
                // 包装成数组
                jsonToParse = "[" + jsonToParse + "]";
            }
            try {
                List<Map> rawCases = JSON.parseArray(jsonToParse, Map.class);
                int index = 1;
                int totalCases = rawCases.size();
                for (Map rawCase : rawCases) {
                    TestCaseGenerationResponse.TestCaseDetail tc = new TestCaseGenerationResponse.TestCaseDetail();
                    tc.setCaseIndex(index++);
                    tc.setInput(String.valueOf(rawCase.get("input")));
                    tc.setExpectedOutput(String.valueOf(rawCase.get("expectedOutput")));
                    // 前2个为样例，其余为隐藏用例
                    tc.setIsPublic(index <= 2 ? 1 : 0);
                    tc.setCaseType(index <= 2 ? "SAMPLE" : "HIDDEN");
                    tc.setGenerationSource("AI");
                    tc.setVersion(1);
                    testCases.add(tc);
                }
            } catch (Exception e) {
                log.warn("[MemoryTool] 解析 testCasesJson 失败: {}", e.getMessage());
            }
        }

        response.setTestCases(testCases);
        return response;
    }

    /**
     * 记录失败案例
     */
    @Tool(name = "recordFailureCase")
    public String recordFailureCase(
            @P("problemType") String problemType,
            @P("failureReason") String failureReason,
            @P("failureDetail") String failureDetail,
            @P("attemptStrategy") String attemptStrategy,
            @P("lessonsLearned") String lessonsLearned,
            @P("problemHash") String problemHash,
            @P("problemTitle") String problemTitle,
            @P("retryCount") int retryCount,
            @P("finalErrorType") String finalErrorType,
            @P("tokenUsed") int tokenUsed,
            @P("modelName") String modelName) {

        log.info("[MemoryTool] 记录失败案例，problemType={}, failureReason={}", problemType, failureReason);

        try {
            FailureCase failureCase = new FailureCase();
            failureCase.setProblemType(problemType);
            failureCase.setFailureReason(failureReason);
            failureCase.setFailureDetail(failureDetail);
            failureCase.setAttemptStrategy(attemptStrategy);
            failureCase.setLessonsLearned(lessonsLearned);
            failureCase.setProblemHash(problemHash);
            failureCase.setProblemTitle(problemTitle);
            failureCase.setRetryCount(retryCount);
            failureCase.setFinalErrorType(finalErrorType);
            failureCase.setTokenUsed(tokenUsed);
            failureCase.setModelName(modelName);
            failureCase.setCreatedAt(LocalDateTime.now());

            caseSearchService.recordFailureCase(failureCase);
            log.info("[MemoryTool] 失败案例记录完成");

            return JSON.toJSONString(new RecordResult(true, "记录成功", null));
        } catch (Exception e) {
            log.error("[MemoryTool] 记录失败案例异常: {}", e.getMessage(), e);
            return JSON.toJSONString(new RecordResult(false, null, e.getMessage()));
        }
    }

    /**
     * 评审测试用例
     *
     * 注意：当返回 passed=true 且 score>=60 时，会自动保存测试用例上下文。
     * 如果 Agent 最终没有调用 recordSuccessCase，系统会自动补调。
     *
     * @param testCasesJson 测试用例 JSON
     * @param problemHash 题目的 contentHash
     * @param problemTitle 题目标题
     * @param problemType 题目类型（来自 analyzeProblemTypeLocal）
     * @param algorithmKeyword 算法关键词（来自 analyzeProblemTypeLocal）
     * @param solutionCode 使用 primarySolutionCode
     * @param suspiciousCaseIndicesJson 可疑用例索引列表（JSON 数组格式），来自 runGeneratorCodeWithVerification
     */
    @Tool(name = "reviewTestCases")
    public String reviewTestCases(
            @P("testCasesJson") String testCasesJson,
            @P(value = "problemHash") String problemHash,
            @P(value = "problemTitle") String problemTitle,
            @P(value = "problemType") String problemType,
            @P(value = "algorithmKeyword") String algorithmKeyword,
            @P(value = "solutionCode") String solutionCode,
            @P(value = "suspiciousCaseIndicesJson", required = false) String suspiciousCaseIndicesJson) {

        log.info("[MemoryTool] 评审测试用例");

        try {
            // 解析可疑用例索引
            java.util.List<Integer> suspiciousIndices = new java.util.ArrayList<>();
            if (suspiciousCaseIndicesJson != null && !suspiciousCaseIndicesJson.isBlank()) {
                try {
                    suspiciousIndices = JSON.parseArray(suspiciousCaseIndicesJson, Integer.class);
                    log.info("[MemoryTool] 收到可疑用例索引: {}", suspiciousIndices);
                } catch (Exception e) {
                    log.warn("[MemoryTool] 解析可疑用例索引失败: {}, 忽略", e.getMessage());
                }
            }

            String jsonToParse = testCasesJson;
            if (testCasesJson != null && testCasesJson.trim().startsWith("[")) {
                jsonToParse = "{\"testCases\":" + testCasesJson + "}";
            }
            TestCaseGenerationResponse response = JSON.parseObject(jsonToParse, TestCaseGenerationResponse.class);

            // 标记可疑用例
            if (!suspiciousIndices.isEmpty() && response.getTestCases() != null) {
                for (int i = 0; i < response.getTestCases().size(); i++) {
                    if (suspiciousIndices.contains(i)) {
                        TestCaseGenerationResponse.TestCaseDetail tc = response.getTestCases().get(i);
                        tc.setSuspicious(true);
                        tc.setSuspiciousReason("双AI验证不一致，建议人工审核或重新生成");
                    }
                }
            }

            ReviewResult result = testCaseReviewer.review(response);

            log.info("[MemoryTool] 评审完成，passed={}, score={}", result.isPassed(), result.getScore());

            // 如果评审成功且分数达标，自动保存上下文用于兜底
            if (result.isPassed() && result.getScore() >= 60) {
                autoSubmitContext.set(new AutoSubmitContext(
                        testCasesJson,
                        problemType,
                        algorithmKeyword,
                        problemHash,
                        problemTitle,
                        solutionCode,
                        System.currentTimeMillis(),
                        0, // tokenUsed 稍后从监听器获取
                        "deepseek-v4-flash"
                ));
                log.info("[MemoryTool] 已自动保存成功上下文，score={}", result.getScore());
            }

            return JSON.toJSONString(result);
        } catch (Exception e) {
            log.error("[MemoryTool] 评审测试用例异常: {}", e.getMessage(), e);
            return JSON.toJSONString(ReviewResult.error(e.getMessage()));
        }
    }

    /**
     * 获取自动提交的上下文（用于兜底机制）
     */
    public static AutoSubmitContext getAutoSubmitContext() {
        return autoSubmitContext.get();
    }

    /**
     * 清除自动提交的上下文（Agent 成功提交后调用）
     */
    public static void clearAutoSubmitContext() {
        autoSubmitContext.set(null);
    }

    /**
     * 分析题目类型（基于规则的本地分析，非 AI）
     * 
     * 注意：此方法使用关键词匹配进行分类，不是 AI 模型分析
     * 适用于快速粗分类，精确分类建议通过 Agent 的推理能力判断
     */
    @Tool(name = "analyzeProblemTypeLocal")
    public String analyzeProblemTypeLocal(
            @P("problemDescription") String problemDescription) {

        log.info("[MemoryTool] 分析题目类型");

        if (StrUtil.isBlank(problemDescription)) {
            return JSON.toJSONString(new ProblemAnalysis(null, null, "无法分析空描述"));
        }

        String lowerDesc = problemDescription.toLowerCase();

        // 题目类型分析
        String problemType = analyzeProblemCategory(lowerDesc);

        // 算法关键词分析
        String keywords = analyzeAlgorithmKeywords(lowerDesc);

        log.info("[MemoryTool] 题目分析完成，type={}, keywords={}", problemType, keywords);

        return JSON.toJSONString(new ProblemAnalysis(problemType, keywords, null));
    }

    private String analyzeProblemCategory(String desc) {
        // 优先匹配更具体的搜索类题目（避免被"排序"等通用词抢走）
        if (desc.contains("搜索") || desc.contains("search") || desc.contains("二分查找") 
                || desc.contains("二分") || desc.contains("binary search")
                || desc.contains("旋转排序") || desc.contains("rotated")) {
            return "SEARCH";
        }
        if (desc.contains("dynamic programming") || desc.contains("dp") || desc.contains("动态规划")) {
            return "DP";
        } else if (desc.contains("最短路径") || desc.contains("dijkstra") || desc.contains("floyd") || desc.contains("单源最短")
                || desc.contains("graph") || desc.contains("bfs") || desc.contains("dfs")) {
            return "GRAPH";
        } else if (desc.contains("最短路") && !desc.contains("动态")) {
            return "GRAPH";
        } else if (desc.contains("并查集") || desc.contains("union find") || desc.contains("disjoint set")) {
            return "UNION_FIND";
        } else if (desc.contains("tree") || desc.contains("二叉树") || desc.contains("遍历")) {
            return "TREE";
        } else if (desc.contains("string") || desc.contains("字符串") || desc.contains("匹配")) {
            return "STRING";
        } else if (desc.contains("sort") || desc.contains("排序")) {
            return "SORT";
        } else if (desc.contains("math") || desc.contains("数学") || desc.contains("素数")) {
            return "MATH";
        } else if (desc.contains("greedy") || desc.contains("贪心")) {
            return "GREEDY";
        } else if (desc.contains("backtrack") || desc.contains("回溯")) {
            return "BACKTRACK";
        } else if (desc.contains("array") || desc.contains("数组")) {
            return "ARRAY";
        } else if (desc.contains("linked list") || desc.contains("链表")) {
            return "LINKED_LIST";
        } else if (desc.contains("stack") || desc.contains("栈")) {
            return "STACK";
        } else if (desc.contains("queue") || desc.contains("队列")) {
            return "QUEUE";
        }
        return "OTHER";
    }

    private String analyzeAlgorithmKeywords(String desc) {
        StringBuilder keywords = new StringBuilder();

        if (desc.contains("dynamic programming") || desc.contains("dp") || desc.contains("动态规划")) {
            keywords.append("动态规划,");
        }
        if (desc.contains("dijkstra") || desc.contains("floyd") || desc.contains("最短路径") || desc.contains("最短路")) {
            keywords.append("最短路径,");
        }
        if (desc.contains("并查集") || desc.contains("union find") || desc.contains("disjoint set")) {
            keywords.append("并查集,");
        }
        if (desc.contains("bfs") || desc.contains("广度优先") || desc.contains("宽度优先")) {
            keywords.append("BFS,");
        }
        if (desc.contains("dfs") || desc.contains("深度优先")) {
            keywords.append("DFS,");
        }
        if (desc.contains("binary search") || desc.contains("二分查找") || desc.contains("二分查找")) {
            keywords.append("二分查找,");
        }
        if (desc.contains("two pointer") || desc.contains("双指针")) {
            keywords.append("双指针,");
        }
        if (desc.contains("sliding window") || desc.contains("滑动窗口")) {
            keywords.append("滑动窗口,");
        }
        if (desc.contains("greedy") || desc.contains("贪心")) {
            keywords.append("贪心,");
        }
        if (desc.contains("backtrack") || desc.contains("回溯")) {
            keywords.append("回溯,");
        }
        if (desc.contains("recursion") || desc.contains("递归")) {
            keywords.append("递归,");
        }
        if (desc.contains("iteration") || desc.contains("迭代")) {
            keywords.append("迭代,");
        }
        if (desc.contains("hash") || desc.contains("哈希")) {
            keywords.append("哈希表,");
        }
        if (desc.contains("heap") || desc.contains("堆") || desc.contains("优先队列")) {
            keywords.append("堆,");
        }
        if (desc.contains("单调栈") || desc.contains("monotonic stack")) {
            keywords.append("单调栈,");
        }
        if (desc.contains("图") || desc.contains("graph")) {
            keywords.append("图论,");
        }

        String result = keywords.toString();
        return result.isEmpty() ? "通用" : result.substring(0, result.length() - 1);
    }

    // 内部类
    private record RecordResult(boolean success, String message, String error) {}
    private record ProblemAnalysis(String problemType, String keywords, String error) {}

    /**
     * 合并查询相似案例（成功+失败）
     * 替代 getSimilarSuccessCases + getSimilarFailureCases，减少一次工具调用
     */
    @Tool(name = "getSimilarCases")
    public String getSimilarCases(
            @P("problemType") String problemType,
            @P("keywords") String keywords,
            @P(value = "limit") Integer limit) {

        log.info("[MemoryTool] 获取相似案例，problemType={}, keywords={}, limit={}", problemType, keywords, limit);

        try {
            // 查询成功案例（只返回摘要，不返回完整 solutionCode）
            List<SuccessCase> successCases = caseSearchService.findSimilarSuccessCases(
                    problemType, keywords, limit);

            // 查询失败案例
            List<FailureCase> failureCases = caseSearchService.findSimilarFailureCases(
                    problemType, keywords, limit);

            // 构建结果（只返回摘要信息）
            List<Map<String, Object>> successSummaries = new ArrayList<>();
            for (SuccessCase sc : successCases) {
                successSummaries.add(Map.of(
                        "problemHash", sc.getProblemHash() != null ? sc.getProblemHash() : "",
                        "problemType", sc.getProblemType() != null ? sc.getProblemType() : "",
                        "algorithmKeyword", sc.getAlgorithmKeyword() != null ? sc.getAlgorithmKeyword() : "",
                        "testcaseCount", sc.getTestcaseCount() != null ? sc.getTestcaseCount() : 0,
                        "successRate", sc.getSuccessRate() != null ? sc.getSuccessRate().doubleValue() : 0.0,
                        "createdAt", sc.getCreatedAt() != null ? sc.getCreatedAt().toString() : ""
                ));
            }

            List<Map<String, Object>> failureSummaries = new ArrayList<>();
            for (FailureCase fc : failureCases) {
                failureSummaries.add(Map.of(
                        "problemHash", fc.getProblemHash() != null ? fc.getProblemHash() : "",
                        "problemType", fc.getProblemType() != null ? fc.getProblemType() : "",
                        "failureReason", fc.getFailureReason() != null ? fc.getFailureReason() : "",
                        "retryCount", fc.getRetryCount() != null ? fc.getRetryCount() : 0,
                        "createdAt", fc.getCreatedAt() != null ? fc.getCreatedAt().toString() : ""
                ));
            }

            SimilarCasesResult result = new SimilarCasesResult(
                    successSummaries,
                    failureSummaries,
                    successSummaries.size(),
                    failureSummaries.size()
            );

            log.info("[MemoryTool] 相似案例查询完成，成功={}, 失败={}", result.totalSuccess(), result.totalFailure());
            return JSON.toJSONString(result);

        } catch (Exception e) {
            log.error("[MemoryTool] 获取相似案例异常: {}", e.getMessage(), e);
            return JSON.toJSONString(new SimilarCasesResult(
                    new ArrayList<>(), new ArrayList<>(), 0, 0));
        }
    }

    /**
     * 相似案例查询结果
     */
    private record SimilarCasesResult(
            List<Map<String, Object>> successCases,
            List<Map<String, Object>> failureCases,
            int totalSuccess,
            int totalFailure
    ) {}
}
