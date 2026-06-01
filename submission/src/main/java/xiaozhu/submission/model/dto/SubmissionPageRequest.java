package xiaozhu.submission.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhu.common.eenum.JudgeStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionPageRequest {
    
    /**
     * 用户ID（必填）
     */
    private Long userId;
    
    /**
     * 页码（默认1）
     */
    private Integer pageNum = 1;
    
    /**
     * 每页条数（默认6）
     */
    private Integer pageSize = 6;
    
    /**
     * 题目标题模糊搜索（可选）
     */
    private String questionTitle;
    
    /**
     * 编程语言筛选（可选）
     */
    private String language;
    
    /**
     * 判题状态筛选（可选）
     */
    private JudgeStatus judgeStatus;
}
