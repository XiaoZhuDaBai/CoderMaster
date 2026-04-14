package xiaozhu.problem.service.testcase;

import xiaozhu.common.message.TestCaseSyncMessage;

/**
 * 测试用例同步服务接口
 * 负责将 AI 生成的测试用例持久化到 MySQL，并更新 delivery bucket
 */
public interface TestCaseSyncService {

    /**
     * 同步 AI 生成的测试用例
     * 1. 遍历保存或更新测试用例到 MySQL
     * 2. 更新 delivery bucket 中的题目（追加测试用例）
     *
     * @param message 测试用例同步消息
     */
    void syncTestCases(TestCaseSyncMessage message);
}
