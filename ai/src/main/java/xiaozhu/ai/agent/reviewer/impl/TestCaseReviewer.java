package xiaozhu.ai.agent.reviewer.impl;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xiaozhu.ai.agent.reviewer.ReviewResult;
import xiaozhu.ai.agent.reviewer.Reviewer;
import xiaozhu.ai.model.TestCaseGenerationResponse;
import xiaozhu.ai.model.TestCaseGenerationResponse.TestCaseDetail;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用例评审器
 *
 * 评审维度：
 * 1. 正确性：solutionCode 已验证，默认为真
 * 2. 覆盖率：是否包含 3 种以上场景类型
 * 3. 多样性：输入输出汉明距离检查
 * 4. 边界覆盖：包含最大/最小/零/负数等边界
 */
@Component
@Slf4j
public class TestCaseReviewer implements Reviewer<TestCaseGenerationResponse> {

    private static final int MIN_COVERAGE_SCENARIOS = 3;
    private static final double MIN_HAMMING_DISTANCE = 0.3;
    private static final int MIN_TEST_CASES = 5;

    @Override
    public ReviewResult review(TestCaseGenerationResponse response) {
        long startTime = System.currentTimeMillis();
        log.info("[TestCaseReviewer] 开始评审测试用例，共 {} 个用例", 
                response.getTestCases() == null ? 0 : response.getTestCases().size());

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        java.util.Map<String, Object> details = new java.util.HashMap<>();

        // 1. 基础校验
        validateBasics(response, issues, details);

        List<TestCaseDetail> testCases = response.getTestCases();
        if (testCases == null || testCases.isEmpty()) {
            issues.add("测试用例列表为空");
            return buildResult(issues, suggestions, details, startTime);
        }

        // 2. 数量检查
        validateCount(testCases, issues, details);

        // 3. 覆盖率检查
        validateCoverage(testCases, issues, details);

        // 4. 边界检查
        validateBoundaryCases(testCases, issues, suggestions, details);

        // 5. 多样性检查
        validateDiversity(testCases, issues, suggestions, details);

        // 6. 格式检查
        validateFormat(testCases, issues);

        int score = calculateScore(issues.size(), testCases.size(), details);

        boolean passed = issues.isEmpty() || score >= 60;
        if (!passed) {
            log.warn("[TestCaseReviewer] 评审未通过，发现问题 {} 个，得分 {}，建议：{}",
                    issues.size(), score, suggestions);
        } else {
            log.info("[TestCaseReviewer] 评审通过，得分 {}", score);
        }

        return ReviewResult.builder()
                .passed(passed)
                .score(score)
                .issues(issues)
                .suggestions(suggestions)
                .details(details)
                .reviewType("TEST_CASE_REVIEW")
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private void validateBasics(TestCaseGenerationResponse response, List<String> issues,
                                 java.util.Map<String, Object> details) {
        if (response == null) {
            issues.add("响应为空");
            return;
        }
        details.put("response_null", false);

        // 检查可疑用例
        List<TestCaseDetail> testCases = response.getTestCases();
        if (testCases != null && !testCases.isEmpty()) {
            List<Integer> suspiciousIndices = new ArrayList<>();
            List<String> suspiciousReasons = new ArrayList<>();

            for (int i = 0; i < testCases.size(); i++) {
                TestCaseDetail tc = testCases.get(i);
                if (tc.getSuspicious() != null && tc.getSuspicious()) {
                    suspiciousIndices.add(i);
                    suspiciousReasons.add(tc.getSuspiciousReason());
                }
            }

            if (!suspiciousIndices.isEmpty()) {
                details.put("suspicious_case_count", suspiciousIndices.size());
                details.put("suspicious_indices", suspiciousIndices);
                details.put("suspicious_reasons", suspiciousReasons);
                issues.add(String.format("存在 %d 个可疑用例（双AI验证不一致），建议人工审核或重新生成",
                        suspiciousIndices.size()));
            }
        }
    }

    private void validateCount(List<TestCaseDetail> testCases, List<String> issues,
                                java.util.Map<String, Object> details) {
        int count = testCases.size();
        details.put("test_case_count", count);

        if (count < MIN_TEST_CASES) {
            issues.add(String.format("测试用例数量不足：共 %d 个，建议至少 %d 个", count, MIN_TEST_CASES));
        }
    }

    private void validateCoverage(List<TestCaseDetail> testCases, List<String> issues,
                                   java.util.Map<String, Object> details) {
        // 统计场景类型
        long publicCount = testCases.stream()
                .filter(tc -> tc.getIsPublic() == 1)
                .count();
        long hiddenCount = testCases.stream()
                .filter(tc -> tc.getIsPublic() == 0)
                .count();
        long sampleCount = testCases.stream()
                .filter(tc -> "SAMPLE".equals(tc.getCaseType()))
                .count();
        long extremeCount = testCases.stream()
                .filter(tc -> "EXTREME".equals(tc.getCaseType()))
                .count();

        details.put("public_case_count", publicCount);
        details.put("hidden_case_count", hiddenCount);
        details.put("sample_case_count", sampleCount);
        details.put("extreme_case_count", extremeCount);

        int scenarioCount = 0;
        if (publicCount > 0) scenarioCount++;
        if (hiddenCount > 0) scenarioCount++;
        if (sampleCount > 0) scenarioCount++;
        if (extremeCount > 0) scenarioCount++;

        details.put("coverage_scenarios", scenarioCount);

        if (scenarioCount < MIN_COVERAGE_SCENARIOS) {
            issues.add(String.format("覆盖率不足：仅覆盖 %d 种场景类型，建议至少 %d 种",
                    scenarioCount, MIN_COVERAGE_SCENARIOS));
        }
    }

    private void validateBoundaryCases(List<TestCaseDetail> testCases, List<String> issues,
                                        List<String> suggestions, java.util.Map<String, Object> details) {
        boolean hasZero = false;
        boolean hasNegative = false;
        boolean hasMax = false;

        for (TestCaseDetail tc : testCases) {
            String input = tc.getInput();
            if (input == null) continue;

            String trimmed = input.trim();
            // 检查边界值
            if ("0".equals(trimmed) || "0\n".equals(trimmed)) {
                hasZero = true;
            }
            if (trimmed.startsWith("-") || input.contains("-")) {
                hasNegative = true;
            }
            // 大值检测（>10000）
            try {
                String numPart = trimmed.split("\\s+")[0];
                if (numPart.matches("\\d{4,}")) {
                    hasMax = true;
                }
            } catch (Exception e) {
                // 忽略解析失败
            }
        }

        details.put("has_zero_case", hasZero);
        details.put("has_negative_case", hasNegative);
        details.put("has_max_case", hasMax);

        if (!hasZero) {
            suggestions.add("缺少边界用例：未包含 0 值测试");
        }
        if (!hasNegative) {
            suggestions.add("缺少边界用例：未包含负数测试");
        }
    }

    private void validateDiversity(List<TestCaseDetail> testCases, List<String> issues,
                                   List<String> suggestions, java.util.Map<String, Object> details) {
        if (testCases.size() < 2) {
            return;
        }

        double avgHammingDistance = calculateAverageHammingDistance(testCases);
        details.put("avg_hamming_distance", avgHammingDistance);

        if (avgHammingDistance < MIN_HAMMING_DISTANCE) {
            issues.add(String.format("多样性不足：用例间平均汉明距离 %.2f，建议 > %.2f",
                    avgHammingDistance, MIN_HAMMING_DISTANCE));
            suggestions.add("建议调整生成策略，增加用例差异度");
        }
    }

    private void validateFormat(List<TestCaseDetail> testCases, List<String> issues) {
        for (int i = 0; i < testCases.size(); i++) {
            TestCaseDetail tc = testCases.get(i);
            if (tc.getInput() == null) {
                issues.add(String.format("用例#%d input 为空", i + 1));
            }
            if (tc.getExpectedOutput() == null) {
                issues.add(String.format("用例#%d expectedOutput 为空", i + 1));
            }
            if (StrUtil.isBlank(tc.getInput()) && StrUtil.isBlank(tc.getExpectedOutput())) {
                issues.add(String.format("用例#%d input 和 expectedOutput 均为空", i + 1));
            }
        }
    }

    private int calculateScore(int issueCount, int testCaseCount, java.util.Map<String, Object> details) {
        int baseScore = 100;

        // 基础扣分（每个问题扣 10 分）
        baseScore -= issueCount * 10;
        if (baseScore < 0) baseScore = 0;

        // 数量奖励
        if (testCaseCount >= 10) {
            baseScore += 5;
        } else if (testCaseCount >= 7) {
            baseScore += 3;
        }

        return baseScore;
    }

    /**
     * 计算用例之间的平均汉明距离（用于评估多样性）
     */
    private double calculateAverageHammingDistance(List<TestCaseDetail> testCases) {
        int n = testCases.size();
        if (n < 2) return 0;

        double totalDistance = 0;
        int pairCount = 0;

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                String s1 = testCases.get(i).getInput();
                String s2 = testCases.get(j).getInput();
                if (s1 == null || s2 == null) continue;

                int len = Math.max(s1.length(), s2.length());
                if (len == 0) continue;

                double distance = hammingDistance(s1, s2) / (double) len;
                totalDistance += distance;
                pairCount++;
            }
        }

        return pairCount > 0 ? totalDistance / pairCount : 0;
    }

    /**
     * 计算两个字符串的汉明距离（处理不同长度）
     */
    private int hammingDistance(String s1, String s2) {
        int len1 = s1.length();
        int len2 = s2.length();
        int maxLen = Math.max(len1, len2);
        int distance = 0;

        for (int i = 0; i < maxLen; i++) {
            char c1 = i < len1 ? s1.charAt(i) : 0;
            char c2 = i < len2 ? s2.charAt(i) : 0;
            if (c1 != c2) {
                distance++;
            }
        }

        return distance;
    }

    private ReviewResult buildResult(List<String> issues, List<String> suggestions,
                                     java.util.Map<String, Object> details, long startTime) {
        return ReviewResult.builder()
                .passed(issues.isEmpty())
                .score(issues.isEmpty() ? 90 : Math.max(0, 90 - issues.size() * 10))
                .issues(issues)
                .suggestions(suggestions)
                .details(details)
                .reviewType("TEST_CASE_REVIEW")
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    @Override
    public String getType() {
        return "TEST_CASE_REVIEWER";
    }
}
