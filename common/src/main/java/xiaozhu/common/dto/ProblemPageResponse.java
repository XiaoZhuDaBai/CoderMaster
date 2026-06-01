package xiaozhu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题目分页查询响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPageResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目列表
     */
    private List<ProblemGenerationResponse> problems;

    /**
     * 总数
     */
    private long total;

    /**
     * 当前页
     */
    private int page;

    /**
     * 每页大小
     */
    private int pageSize;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 可用标签列表（用于前端动态生成主题选项）
     */
    private List<String> availableTags;
}
