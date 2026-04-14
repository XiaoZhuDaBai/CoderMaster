package xiaozhu.common.dto;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI生成测试用例响应数据
 * 用于在服务间传递测试用例信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseGenerationResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 测试用例列表
     */
    private List<TestCaseDetail> testCases;

    /**
     * 测试用例详情
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TestCaseDetail implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 用例序号（从1开始递增）
         */
        private Integer caseIndex;

        /**
         * 标准输入
         */
        private String input;

        /**
         * 期望输出
         */
        private String expectedOutput;

        /**
         * 是否公开：0-隐藏，1-公开
         */
        private Integer isPublic;

        /**
         * 用例类型：SAMPLE/HIDDEN/EXTREME
         */
        private String caseType;

        /**
         * 权重（默认1.00）
         */
        private Double weight;

        /**
         * 生成来源（固定"AI"）
         */
        private String generationSource;

        /**
         * 版本号（默认1）
         */
        private Integer version;

        /**
         * 内容哈希（输入与输出拼接后的SHA-256）
         */
        private String contentHash;

        /**
         * 该用例时间限制（ms，NULL使用题目默认值）
         */
        private Integer timeLimit;

        /**
         * 该用例内存限制（MB，NULL使用题目默认值）
         */
        private Integer memoryLimit;
    }
}

