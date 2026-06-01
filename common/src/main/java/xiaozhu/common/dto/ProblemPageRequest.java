package xiaozhu.common.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题目分页查询请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemPageRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 搜索关键词（匹配标题、描述、标签）
     */
    private String searchKeyword;

    /**
     * 难度筛选 (0-简单, 1-中等, 2-困难, null 表示全部)
     */
    private Integer difficulty;

    /**
     * 标签筛选列表（多选）
     */
    private List<String> tagNames;

    /**
     * 页码（从 1 开始）
     */
    private Integer page = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;
}
