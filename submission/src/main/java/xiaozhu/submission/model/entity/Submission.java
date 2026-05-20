package xiaozhu.submission.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import xiaozhu.common.eenum.JudgeStatus;

import java.time.LocalDateTime;

@Data
@TableName("submission")
public class Submission {

    @TableId(value = "submission_id", type = IdType.AUTO)
    private Long submissionId;

    private Long userId;

    private Long questionId;

    @TableField(select = false)
    private String code;

    private String language;

    private JudgeStatus judgeStatus;

    private String judgeResult;

    private Integer totalCases;

    private Integer passedCases;

    private Long timeCost;

    private Long memoryCost;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime judgeTime;
}

