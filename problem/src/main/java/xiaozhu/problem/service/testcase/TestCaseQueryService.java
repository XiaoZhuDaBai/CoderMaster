package xiaozhu.problem.service.testcase;

import xiaozhu.common.dto.TestCaseGenerationResponse;

import java.util.List;

/**
 * 测试用例查询服务接口
 * 负责根据 contentHash 查询测试用例（Redis 优先，未命中查 MySQL 并回写）
 */
public interface TestCaseQueryService {

    /**
     * 根据 contentHash 获取测试用例
     * 流程：Redis → 未命中查 MySQL → 回写 Redis
     *
     * @param contentHash 题目内容哈希
     * @return 测试用例列表
     */
    List<TestCaseGenerationResponse.TestCaseDetail> getTestCasesByContentHash(String contentHash);
}
