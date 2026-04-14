package xiaozhu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * 沙箱执行请求
 * 仅包含执行代码所需的最少字段，用于 ai-service 调用 judge-service 的沙箱
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SandboxExecuteRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 代码内容
     */
    private String code;

    /**
     * 语言标识（固定为 java）
     */
    private String language = "java";

    /**
     * 标准输入（对数器通过 stdin 读取参数）
     */
    private String userInput;
}
