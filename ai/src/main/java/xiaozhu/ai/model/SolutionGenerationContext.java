package xiaozhu.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题解生成上下文，封装所有必要的变量
 *
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/01/01
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolutionGenerationContext {

    // 请求相关变量
    private String requestTags;
    private String requestDifficulty;
    private String requestSource;
    private String requestQuestionType;
    private String requestTimeLimit;
    private String requestMemoryLimit;
    private String requestAdditionalRequirements;

    // 问题相关变量
    private String problemTitle;
    private String problemDescription;
    private String problemInputSection;
    private String problemOutputSection;
    private String problemExampleSection;
    private String problemTestCaseSection;

    private String problemTags;
    private String problemDifficulty;
    private String problemTimeLimit;
    private String problemMemoryLimit;
    private String problemStackLimit;
}
