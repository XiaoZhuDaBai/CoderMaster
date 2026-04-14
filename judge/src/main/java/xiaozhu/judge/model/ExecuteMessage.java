package xiaozhu.judge.model;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 记录单个测试用例或单次执行的产出
 */
@Data
@Accessors(chain = true)
public class ExecuteMessage {
    /**
     * 标准输出
     */
    private String message;

    /**
     * 错误输出
     */
    private String errorMessage;

    /**
     * 执行耗时（毫秒）
     */
    private Long time = 0L;

    /**
     * 内存占用（字节）
     */
    private Long memory = 0L;

    /**
     * 进程退出码
     */
    private Long exitValue = 0L;

    /**
     * 当前测试用例是否通过
     */
    private boolean correct = true;
}

