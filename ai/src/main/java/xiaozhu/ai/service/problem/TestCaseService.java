package xiaozhu.ai.service.problem;

import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.TestCaseGenerationResponse;

/**
 * 测试用例生成服务接口
 */
public interface TestCaseService {

    /**
     * 生成测试用例（沙箱验算模式）
     *
     * 流程：AI 生成 solutionCode → 沙箱验证 → AI 生成 generatorCode → 沙箱执行得到 verified expectedOutput
     * expectedOutput 完全由 solutionCode 执行产生，彻底消除 AI 幻觉
     *
     * @param problem 题目信息
     * @return verified 测试用例
     * @throws xiaozhu.ai.exception.AiGenerationException 生成失败时抛出异常
     * @deprecated 已废弃，请使用 {@link xiaozhu.ai.agent.service.TestCaseGenerationAgentService#generate}
     */
    @Deprecated
    TestCaseGenerationResponse generateVerifiedTestCases(ProblemGenerationResponse problem);

    /**
     * 检查测试用例是否已存在
     *
     * @param userKey     用户标识
     * @param contentHash 题目内容哈希
     * @return 是否存在
     */
    boolean testCasesExist(String userKey, String contentHash);

    /**
     * 保存测试用例到 Redis
     *
     * @param userKey     用户标识
     * @param contentHash 题目内容哈希
     * @param response    测试用例响应
     */
    void saveTestCases(String userKey, String contentHash, TestCaseGenerationResponse response);
}
