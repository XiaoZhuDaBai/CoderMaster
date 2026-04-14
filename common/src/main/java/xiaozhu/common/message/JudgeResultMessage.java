package xiaozhu.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhu.common.eenum.JudgeStatus;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 判题结果消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResultMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long submissionId;

    /**
     * 用于运行案例结果的自定义请求 ID
     */
    private String requestId;

    private JudgeStatus judgeStatus;

    private Integer totalCases;

    private Integer passedCases;

    private Long timeCost;

    private Long memoryCost;

    private String errorMessage;

    /**
     * 判题原始结果，JSON 字符串，方便落库
     */
    private String judgeResult;

    /**
     * 执行输出
     */
    private List<String> outputList;

    private LocalDateTime judgeTime;
}

