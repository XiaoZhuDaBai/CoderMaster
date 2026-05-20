package xiaozhu.ai.agent.reviewer;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 评审结果
 *
 * @param passed 是否通过评审
 * @param score 评分（0-100）
 * @param issues 问题列表（未通过时填充）
 * @param suggestions 建议列表（用于改进）
 * @param details 详细评审信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResult {

    /**
     * 是否通过评审
     */
    private boolean passed;

    /**
     * 评分（0-100）
     */
    private int score;

    /**
     * 问题列表
     */
    private List<String> issues;

    /**
     * 改进建议
     */
    private List<String> suggestions;

    /**
     * 详细评审信息（键值对）
     */
    private java.util.Map<String, Object> details;

    /**
     * 评审类型
     */
    private String reviewType;

    /**
     * 评审耗时（毫秒）
     */
    private long durationMs;

    public static ReviewResult pass(int score) {
        return ReviewResult.builder()
                .passed(true)
                .score(score)
                .issues(List.of())
                .suggestions(List.of())
                .build();
    }

    public static ReviewResult fail(int score, List<String> issues, List<String> suggestions) {
        return ReviewResult.builder()
                .passed(false)
                .score(score)
                .issues(issues)
                .suggestions(suggestions)
                .build();
    }

    public static ReviewResult error(String message) {
        return ReviewResult.builder()
                .passed(false)
                .score(0)
                .issues(List.of("评审系统错误: " + message))
                .suggestions(List.of())
                .build();
    }
}
