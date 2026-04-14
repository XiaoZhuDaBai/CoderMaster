package xiaozhu.submission.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RunCaseRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    @NotBlank(message = "代码不能为空")
    private String code;

    @NotBlank(message = "语言不能为空")
    private String language;

    /**
     * 题目标识（数字 ID 或哈希），可选
     */
    private String questionId;

    /**
     * 题面快照，JSON 字符串（可选）
     */
    private String questionSnapshot;

    /**
     * 题目内容哈希，用于确定题目版本
     */
    private String contentHash;

    /**
     * 用户自定义输入
     */
    private String userInput;
}

