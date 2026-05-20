package xiaozhu.ai.memory;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 案例检索日志实体
 */
@Data
@TableName("ai_case_query_log")
public class CaseQueryLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String queryType;

    private String problemHash;

    private String problemType;

    private String keywords;

    private Integer resultCount;

    private BigDecimal hitRate;

    private Integer queryDurationMs;

    private Boolean usedInGeneration;

    private LocalDateTime createdAt;
}
