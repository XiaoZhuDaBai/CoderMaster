package xiaozhu.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import xiaozhu.problem.entity.Question;

/**
 * 题目 Mapper
 */
@Mapper
public interface QuestionMapper extends BaseMapper<Question> {
}
