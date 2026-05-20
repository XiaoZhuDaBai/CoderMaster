package xiaozhu.ai.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 生成失败案例实体
 */
@Data
@TableName("ai_failure_case")
public class FailureCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String problemType;

    private String failureReason;

    private String failureDetail;

    private String attemptStrategy;

    private String lessonsLearned;

    private String problemHash;

    private String problemTitle;

    private Integer retryCount;

    private String finalErrorType;

    private Integer tokenUsed;

    private String modelName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
