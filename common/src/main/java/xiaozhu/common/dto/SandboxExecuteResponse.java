package xiaozhu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 沙箱执行响应
 * 仅包含执行结果的最少字段，用于 ai-service 获取对数器输出
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SandboxExecuteResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 执行是否成功
     */
    private boolean success;

    /**
     * 退出码（0表示成功）
     */
    private Long exitCode;

    /**
     * 标准输出列表
     */
    private List<String> rawOutputList = new ArrayList<>();

    /**
     * 错误信息列表
     */
    private List<String> errorMessages;
}
