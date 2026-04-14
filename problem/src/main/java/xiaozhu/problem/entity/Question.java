package xiaozhu.problem.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 题目实体类
 */
@Data
@TableName("question")
public class Question implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @TableId(value = "question_id", type = IdType.AUTO)
    private Long questionId;

    private String questionCode;

    private String title;

    private Integer difficulty;

    private Integer questionType;

    private String source;

    private Long authorId;

    private String description;

    private String inputDesc;

    private String outputDesc;

    private String examples;

    private Integer timeLimit;

    private Integer memoryLimit;

    private Integer stackLimit;

    private Integer status;

    private Integer version;

    private LocalDateTime publishedTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /**
     * 题目内容哈希，用于关联测试用例
     */
    private String contentHash;
}
