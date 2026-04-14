package xiaozhu.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成测试用例的响应数据（generatorCode 生成阶段）
 *
 * 流程后半段使用：
 * - generatorCode：Java 测试数据生成器代码（只输出 input，不输出 expectedOutput）
 * - generatorArgs：生成器的随机种子
 *
 * solutionCode 由独立的 solution 生成流程管，验证通过后才传入此阶段。
 *
 * @author XiaoZhuDaBai
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifiedTestCaseGenerationResponse {

    /**
     * Java 正解代码
     * 经过样例验证，确保算法逻辑正确
     * 用于在沙箱中跑 generatorCode 产生的输入，获取真实 expectedOutput
     */
    private String solutionCode;

    /**
     * Java 测试数据生成器代码
     * 读取 generatorArgs（随机种子），按分档策略生成测试输入，只输出 rawInput
     *
     * 输出格式（每行一个完整输入）：
     * ---INPUT---
     * [完整输入字符串，换行用 \n 表示]
     * ---INPUT---
     * [下一个完整输入]
     * ...（重复）
     */
    private String generatorCode;

    /**
     * 生成器的随机种子
     * 格式：纯数字字符串（随机种子）
     * 示例："42"、"20240321"
     */
    private String generatorArgs;
}
