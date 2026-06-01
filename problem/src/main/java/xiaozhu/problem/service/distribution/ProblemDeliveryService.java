package xiaozhu.problem.service.distribution;

import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.ProblemPageRequest;
import xiaozhu.common.dto.ProblemPageResponse;

import java.util.List;

/**
 * 题目交付服务接口
 * 负责 AI 生成题目的缓存传输区管理（从 Redis 源数据到 delivery bucket）
 */
public interface ProblemDeliveryService {

    /**
     * 从 AI 服务存储的原始 key 中读取题目并缓存到传输区（通过消息队列接收的新生成题目）
     *
     * @param userKey     用户标识
     * @param contentHash 内容哈希
     * @param generatedAt 题目生成时间戳（毫秒）
     */
    void cacheProblemFromSource(String userKey, String contentHash, long generatedAt);

    /**
     * 从 AI 服务存储的原始 key 中读取题目并缓存到传输区（从索引加载的历史题目）
     *
     * @param userKey     用户标识
     * @param contentHash 内容哈希
     */
    void cacheProblemFromSource(String userKey, String contentHash);

    /**
     * 获取指定用户可用的题目列表（所有题目）
     *
     * @param userKey 用户标识
     * @return 题目列表
     */
    List<ProblemGenerationResponse> listProblems(String userKey);

    /**
     * 获取指定用户的题目列表并按生成时间排序（最近生成的先返回）
     *
     * @param userKey 用户标识
     * @return 题目列表（按生成时间降序）
     */
    List<ProblemGenerationResponse> listProblemsSorted(String userKey);

    /**
     * 获取指定用户新生成的题目列表（24小时内生成的题目）
     *
     * @param userKey 用户标识
     * @return 新题目列表
     */
    List<ProblemGenerationResponse> listNewProblems(String userKey);

    /**
     * 根据 contentHash 获取题目（Cache-Aside 模式）
     *
     * @param contentHash 题目内容哈希
     * @param userKey     用户标识（用于缓存 key 隔离）
     * @return 题目详情，未找到返回 null
     */
    ProblemGenerationResponse getProblemByContentHash(String contentHash, String userKey);

    /**
     * 分页查询题目（支持搜索和筛选）
     *
     * @param userKey 用户标识
     * @param request 分页请求参数
     * @return 分页响应
     */
    ProblemPageResponse listProblemsPaged(String userKey, ProblemPageRequest request);
}
