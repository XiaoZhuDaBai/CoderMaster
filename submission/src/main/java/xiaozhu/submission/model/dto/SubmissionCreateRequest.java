package xiaozhu.submission.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class SubmissionCreateRequest {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    /**
     * 题目唯一标识，可为数字 ID 或哈希
     */
    private String questionId;

    private Integer questionVersion;

    private Integer testSetVersion;

    @NotBlank(message = "代码不能为空")
    private String code;

    @NotBlank(message = "语言不能为空")
    private String language;

    /**
     * 题面快照，JSON 字符串（可选）
     */
    private String questionSnapshot;

    /**
     * 题目内容哈希，用于从Redis获取测试用例
     */
    private String contentHash;
}

