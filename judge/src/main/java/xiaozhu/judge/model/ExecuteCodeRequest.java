package xiaozhu.judge.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import xiaozhu.common.dto.TestCaseDTO;

import java.util.List;

/**
 * 判题请求载体，描述一次代码执行所需的上下文
 */
@Data
public class ExecuteCodeRequest {

    /**
     * 用户代码
     */
    @NotBlank(message = "代码内容不能为空")
    private String code;

    /**
     * 语言标识（如 java、cpp）
     */
    @NotBlank(message = "语言不能为空")
    private String language;

    /**
     * 题目编号（用于加载测试数据）
     */
    private String problemId;

    /**
     * 用户自定义输入（用户测试模式）
     */
    private String userInput;

    /**
     * 测试用例列表（从Redis获取，用于正式判题）
     */
    private List<TestCaseDTO> testCases;
}
