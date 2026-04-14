package xiaozhu.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 测试用例实体类
 */
@Data
@TableName("question_test_case")
public class QuestionTestCase implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "case_id", type = IdType.AUTO)
    private Long caseId;

    private Long questionId;

    private Integer caseIndex;

    private String input;

    private String expectedOutput;

    private Integer isPublic;

    private String caseType;

    private BigDecimal weight;

    private String generationSource;

    private Integer version;

    /**
     * 用例内容哈希，用于去重和关联
     */
    private String contentHash;

    private Integer timeLimit;

    private Integer memoryLimit;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
