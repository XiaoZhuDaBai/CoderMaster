package xiaozhu.problem.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import xiaozhu.problem.entity.QuestionTestCase;

import java.util.List;

/**
 * 测试用例 Mapper
 */
@Mapper
public interface QuestionTestCaseMapper extends BaseMapper<QuestionTestCase> {

    /**
     * 根据 contentHash 查询测试用例列表
     */
    List<QuestionTestCase> selectByContentHash(String contentHash);

    /**
     * 插入测试用例（使用 LONGVARCHAR 避免大文本截断）
     */
    int insertTestCase(QuestionTestCase testCase);

    /**
     * 更新测试用例（使用 LONGVARCHAR 避免大文本截断）
     */
    int updateTestCase(QuestionTestCase testCase);
}
