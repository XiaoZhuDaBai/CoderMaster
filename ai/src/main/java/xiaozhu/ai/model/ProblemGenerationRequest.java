package xiaozhu.ai.model;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI生成题目请求参数
 * 
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/16 2:32
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProblemGenerationRequest {
    
    /**
     * 标签ID列表（算法/数据结构标签）
     * 例如：数组、动态规划、双指针
     */
    @NotEmpty(message = "标签列表不能为空")
    private List<String> tagIds;
    
    /**
     * 难度：简单，中等，困难
     */
    @NotNull(message = "难度不能为空")
    private String difficulty;
    
    /**
     * 题目场景/来源：竞赛、面试、练习等
     * 例如："竞赛"、"面试"、"LeetCode风格"、"NowCoder风格"
     */
    private String source;
    
    /**
     * 题目类型：0-ACM，1-OI
     * 默认0（ACM模式）
     */
    @Builder.Default
    private Integer questionType = 0;

    /**
     * 生成题目数量（1-4）
     */
    @Min(value = 1, message = "生成数量最小为1")
    @Max(value = 4, message = "生成数量最大为4")
    private Integer number;
    
    /**
     * 时间限制（毫秒）
     * 默认1000ms，可选
     */
    private Integer timeLimit;
    
    /**
     * 内存限制（MB）
     * 默认256MB，可选
     */
    private Integer memoryLimit;
    
    /**
     * 额外要求/提示（可选）
     * 用户可以添加额外的生成要求，如"需要用到滑动窗口"、"要求时间复杂度O(n)"
     */
    private String additionalRequirements;

    /**
     * 触发生成的用户唯一标识（如 uuid 或 userId）
     */
    @NotEmpty(message = "用户标识不能为空")
    private String userUuid;
}
