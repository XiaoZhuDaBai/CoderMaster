package xiaozhu.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhu.common.eenum.JudgeTaskType;

import java.io.Serial;
import java.io.Serializable;

/**
 * 发送到判题服务的任务消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeTaskMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 提交 ID，运行案例时可为空
     */
    private Long submissionId;

    private Long userId;

    private Long questionId;

    private Integer questionVersion;

    private Integer testSetVersion;

    private String code;

    private String language;

    /**
     * 题面快照，JSON 字符串
     */
    private String questionSnapshot;

    /**
     * 用户输入，用于运行案例
     */
    private String userInput;

    /**
     * 消息来源
     */
    private JudgeTaskType taskType;

    /**
     * 自定义请求标识，用于运行案例结果关联
     */
    private String requestId;

    /**
     * 题目内容哈希，用于从Redis获取测试用例
     */
    private String contentHash;
}

