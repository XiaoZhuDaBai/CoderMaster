package xiaozhu.common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成题目的响应数据
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemGenerationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 题目标题 */
    private String title;

    /** 题目描述 */
    private String description;

    /** 输入描述 */
    private String inputDesc;

    /** 输出描述 */
    private String outputDesc;

    /**
     * 样例（JSON 格式字符串）
     * 例如：[{"input": "1 2", "output": "3", "explanation": "示例"}]
     */
    private String examples;

    /** 测试用例列表 */
    private List<TestCase> testCases;

    /** 标签 ID 列表 */
    private List<Integer> tagIds;

    /** 标签名称列表 */
    private List<String> tagNames;

    /** 难度：0-简单，1-中等，2-困难 */
    private Integer difficulty;

    /** 题目类型：0-ACM，1-OI */
    private Integer questionType;

    /** 来源/场景 */
    private String source;

    /** 时间限制（毫秒） */
    private Integer timeLimit;

    /** 内存限制（MB） */
    private Integer memoryLimit;

    /** 栈限制（MB） */
    private Integer stackLimit;

    /** 题目内容哈希，用于去重 */
    private String contentHash;

    /** 题目生成时间（毫秒时间戳） */
    private Long generatedAt;

    /** 是否新题目（24小时内生成） */
    private Boolean isNew;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCase implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /** 用例序号（从 1 开始） */
        private Integer caseIndex;

        /** 标准输入 */
        private String input;

        /** 标准输出 */
        private String expectedOutput;

        /**
         * 是否公开：0-隐藏，1-公开，默认 0
         */
        private Integer isPublic = 0;

        /** 用例类型：SAMPLE/HIDDEN/EXTREME */
        private String caseType;

        /** 权重（默认 1.00） */
        private Double weight;

        /** 用例时间限制（ms），为空则继承题目配置 */
        private Integer timeLimit;

        /** 用例内存限制（MB），为空则继承题目配置 */
        private Integer memoryLimit;
    }
}


