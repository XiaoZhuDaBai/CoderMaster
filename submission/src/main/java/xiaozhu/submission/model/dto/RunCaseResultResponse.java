package xiaozhu.submission.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhu.common.eenum.JudgeStatus;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class RunCaseResultResponse {

    private String requestId;

    private JudgeStatus judgeStatus;

    private String judgeResult;

    private String errorMessage;

    private Long timeCost;

    private Long memoryCost;
}


