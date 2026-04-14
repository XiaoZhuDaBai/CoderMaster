package xiaozhu.submission.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhu.submission.model.entity.Submission;

@Mapper
public interface SubmissionMapper extends BaseMapper<Submission> {
}

