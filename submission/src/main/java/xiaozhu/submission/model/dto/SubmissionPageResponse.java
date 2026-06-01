package xiaozhu.submission.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubmissionPageResponse {
    
    /**
     * 提交记录列表
     */
    private List<SubmissionResponse> records;
    
    /**
     * 总记录数
     */
    private long total;
    
    /**
     * 当前页码
     */
    private int pageNum;
    
    /**
     * 每页条数
     */
    private int pageSize;
    
    /**
     * 总页数
     */
    private int totalPages;
}
