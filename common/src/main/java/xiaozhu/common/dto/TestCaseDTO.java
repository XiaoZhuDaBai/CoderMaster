package xiaozhu.common.dto;

import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 测试用例数据传输对象
 * 用于在服务间传递测试用例信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 用例序号（从1开始）
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
     * 用例时间限制（ms），为空则使用题目默认值
     */
    private Integer timeLimit;

    /**
     * 用例内存限制（MB），为空则使用题目默认值
     */
    private Integer memoryLimit;
}

