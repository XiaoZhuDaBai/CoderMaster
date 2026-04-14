package xiaozhu.common.message;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 题目生成完成事件消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemGeneratedMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 触发事件的用户唯一标识
     */
    private String userKey;

    /**
     * 题目内容哈希
     */
    private String contentHash;

    /**
     * 在 Redis 中存储题目的 key
     */
    private String redisKey;

    /**
     * 请求追踪 ID，便于调试
     */
    private String requestId;

    /**
     * 事件产生时间戳
     */
    @Builder.Default
    private long occurredAt = Instant.now().toEpochMilli();
}


