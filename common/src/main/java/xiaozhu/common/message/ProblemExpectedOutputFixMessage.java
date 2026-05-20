package xiaozhu.common.message;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 题目自带 expectedOutput 修正消息
 *
 * <p>当双 AI 交叉验证发现题目自带的 expectedOutput 错误时（情况②或④：
 * 两个 AI 独立生成的 solutionCode 输出一致，但与题目自带的 expectedOutput 不符），
 * 立即发送此消息通知 problem-service 更新缓存和数据库。</p>
 *
 * <p>修正逻辑：
 * <ul>
 *   <li>Redis: 更新 delivery bucket 中对应 testCase 的 expectedOutput</li>
 *   <li>MySQL: 更新 question_test_case 表中对应记录的 expectedOutput（如已存在）</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemExpectedOutputFixMessage implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 题目内容哈希
     */
    private String contentHash;

    /**
     * 用户标识（用于定位 delivery bucket）
     */
    private String userKey;

    /**
     * 被修正的用例列表
     */
    private List<CaseFix> cases;

    /**
     * 修正原因描述（如 "sandboxA==sandboxB!=expectedOutput"）
     */
    private String correctionReason;

    /**
     * 消息发送时间戳
     */
    private long occurredAt;

    /**
     * 单个用例的修正信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseFix implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /** 用例序号（0 或 1，对应题目自带的前两个用例） */
        private int caseIndex;

        /** 原错误值（用于日志对比） */
        private String originalExpectedOutput;

        /** 修正后的正确值（来自沙箱实际执行输出） */
        private String correctedExpectedOutput;
    }
}
