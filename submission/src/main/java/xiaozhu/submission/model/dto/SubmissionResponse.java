package xiaozhu.submission.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhu.common.eenum.JudgeStatus;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionResponse {
    private Long submissionId;
    private Long userId;
    private Long questionId;
    private String code;  // 提交的代码
    private String title;
    private Integer difficulty;
    private String language;
    private JudgeStatus judgeStatus;
    private String judgeResult;
    private Integer totalCases;
    private Integer passedCases;
    private Long timeCost;
    private Long memoryCost;
    private String errorMessage;
    private List<String> outputList;
    private LocalDateTime createTime;
    private LocalDateTime judgeTime;
    // 完整题目详情
    private String description;
    private String inputDesc;
    private String outputDesc;
    private String examples;
    private Integer timeLimit;
    private Integer memoryLimit;
}

