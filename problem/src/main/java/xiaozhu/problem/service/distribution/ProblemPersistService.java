package xiaozhu.problem.service.distribution;

/**
 * 题目持久化服务接口
 * 负责将 AI 生成的题目从 Redis 持久化到 MySQL，以及清理源数据
 */
public interface ProblemPersistService {

    /**
     * 持久化题目到 MySQL
     * 从 Redis 读取题目内容，转换为实体后写入数据库
     *
     * @param userKey     用户标识
     * @param contentHash 内容哈希
     * @return 题目ID，持久化失败返回 null
     */
    Long persistToMySQL(String userKey, String contentHash);

    /**
     * 删除 AI 生成题目的源数据 key
     * 在持久化和缓存传输都完成后调用，释放 Redis 空间
     *
     * @param userKey     用户标识
     * @param contentHash 内容哈希
     */
    void deleteSourceKey(String userKey, String contentHash);
}
