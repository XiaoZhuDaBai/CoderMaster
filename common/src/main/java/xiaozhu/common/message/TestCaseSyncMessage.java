package xiaozhu.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import xiaozhu.common.dto.TestCaseGenerationResponse;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 测试用例同步消息
 * 用于 AI 生成测试用例后，同步到 MySQL
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TestCaseSyncMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目内容哈希
     */
    private String contentHash;

    /**
     * 用户标识（用于更新 delivery bucket）
     */
    private String userKey;

    /**
     * 测试用例列表
     */
    private List<TestCaseGenerationResponse.TestCaseDetail> testCases;
}
