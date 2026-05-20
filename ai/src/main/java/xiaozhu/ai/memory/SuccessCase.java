package xiaozhu.ai.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 生成成功案例实体
 */
@Data
@TableName("ai_success_case")
public class SuccessCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String problemType;

    private String algorithmKeyword;

    private String generationStrategy;

    private Integer testcaseCount;

    private BigDecimal successRate;

    private String problemHash;

    private String problemTitle;

    private String contextSummary;

    private String solutionCodeHash;

    private Integer generationDurationMs;

    private Integer tokenUsed;

    private String modelName;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
