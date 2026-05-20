package xiaozhu.ai.agent.reviewer.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xiaozhu.ai.agent.reviewer.ReviewResult;
import xiaozhu.ai.agent.reviewer.Reviewer;
import xiaozhu.ai.model.ProblemGenerationRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * 题目评审器
 *
 * 评审题目请求完整性：
 * 1. 标签列表非空
 * 2. 难度等级有效
 * 3. 时间/内存限制合理
 */
@Component
@Slf4j
public class ProblemReviewer implements Reviewer<ProblemGenerationRequest> {

    private static final int MIN_TIME_LIMIT = 100;
    private static final int MAX_TIME_LIMIT = 10000;
    private static final int MIN_MEMORY_LIMIT = 16;
    private static final int MAX_MEMORY_LIMIT = 1024;

    private static final List<String> VALID_DIFFICULTIES = List.of("简单", "中等", "困难");

    @Override
    public ReviewResult review(ProblemGenerationRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("[ProblemReviewer] 开始评审题目请求，用户={}", request.getUserUuid());

        List<String> issues = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        java.util.Map<String, Object> details = new java.util.HashMap<>();

        // 1. 基础信息检查
        validateBasics(request, issues, suggestions, details);

        // 2. 限制参数检查
        validateLimits(request, issues, suggestions, details);

        int score = calculateScore(issues.size(), details);
        boolean passed = issues.isEmpty() || score >= 70;

        if (!passed) {
            log.warn("[ProblemReviewer] 评审未通过，发现问题 {} 个，得分 {}", issues.size(), score);
        } else {
            log.info("[ProblemReviewer] 评审通过，得分 {}", score);
        }

        return ReviewResult.builder()
                .passed(passed)
                .score(score)
                .issues(issues)
                .suggestions(suggestions)
                .details(details)
                .reviewType("PROBLEM_REVIEW")
                .durationMs(System.currentTimeMillis() - startTime)
                .build();
    }

    private void validateBasics(ProblemGenerationRequest request, List<String> issues,
                                List<String> suggestions, java.util.Map<String, Object> details) {
        if (request.getTagIds() == null || request.getTagIds().isEmpty()) {
            issues.add("标签列表为空");
            suggestions.add("请提供至少一个算法/数据结构标签");
        } else {
            details.put("tag_count", request.getTagIds().size());
            details.put("tags", request.getTagIds());
        }

        if (request.getDifficulty() == null || request.getDifficulty().isBlank()) {
            issues.add("难度等级为空");
            suggestions.add("请指定难度：简单、中等、困难");
        } else {
            String difficulty = request.getDifficulty();
            details.put("difficulty", difficulty);
            if (!VALID_DIFFICULTIES.contains(difficulty)) {
                issues.add("难度等级无效：" + difficulty);
                suggestions.add("有效难度值为：简单、中等、困难");
            }
        }

        if (request.getUserUuid() == null || request.getUserUuid().isBlank()) {
            issues.add("用户标识为空");
        }
    }

    private void validateLimits(ProblemGenerationRequest request, List<String> issues,
                                 List<String> suggestions, java.util.Map<String, Object> details) {
        // 时间限制检查
        if (request.getTimeLimit() != null) {
            int timeLimit = request.getTimeLimit();
            details.put("time_limit", timeLimit);

            if (timeLimit < MIN_TIME_LIMIT) {
                issues.add("时间限制过小（" + timeLimit + "ms），最小为 " + MIN_TIME_LIMIT + "ms");
                suggestions.add("建议将时间限制调整为 " + MIN_TIME_LIMIT + "ms 以上");
            } else if (timeLimit > MAX_TIME_LIMIT) {
                issues.add("时间限制过大（" + timeLimit + "ms），最大为 " + MAX_TIME_LIMIT + "ms");
                suggestions.add("建议将时间限制调整为 " + MAX_TIME_LIMIT + "ms 以内");
            }
        } else {
            details.put("time_limit", "DEFAULT");
        }

        // 内存限制检查
        if (request.getMemoryLimit() != null) {
            int memoryLimit = request.getMemoryLimit();
            details.put("memory_limit", memoryLimit);

            if (memoryLimit < MIN_MEMORY_LIMIT) {
                issues.add("内存限制过小（" + memoryLimit + "MB），最小为 " + MIN_MEMORY_LIMIT + "MB");
                suggestions.add("建议将内存限制调整为 " + MIN_MEMORY_LIMIT + "MB 以上");
            } else if (memoryLimit > MAX_MEMORY_LIMIT) {
                issues.add("内存限制过大（" + memoryLimit + "MB），最大为 " + MAX_MEMORY_LIMIT + "MB");
                suggestions.add("建议将内存限制调整为 " + MAX_MEMORY_LIMIT + "MB 以内");
            }
        } else {
            details.put("memory_limit", "DEFAULT");
        }
    }

    private int calculateScore(int issueCount, java.util.Map<String, Object> details) {
        int baseScore = 100 - issueCount * 15;

        // 标签数量奖励
        Integer tagCount = (Integer) details.getOrDefault("tag_count", 0);
        if (tagCount >= 3) {
            baseScore += 5;
        } else if (tagCount >= 2) {
            baseScore += 3;
        }

        // 有额外要求加 2 分
        if (details.get("has_additional_requirements") != null) {
            baseScore += 2;
        }

        return Math.min(100, Math.max(0, baseScore));
    }

    @Override
    public String getType() {
        return "PROBLEM_REVIEWER";
    }
}
