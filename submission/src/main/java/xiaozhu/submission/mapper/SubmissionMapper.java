package xiaozhu.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhu.submission.model.entity.Submission;

import java.util.List;

@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {
    
    /**
     * 按题目标题模糊搜索查询 submission IDs
     * @param userId 用户ID
     * @param title 题目标题（支持模糊匹配）
     * @return 匹配的 submission IDs
     */
    List<Long> selectSubmissionIdsByTitle(Long userId, String title);
}

