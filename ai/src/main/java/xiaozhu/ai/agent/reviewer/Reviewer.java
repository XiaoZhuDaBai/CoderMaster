package xiaozhu.ai.agent.reviewer;

/**
 * 评审器接口
 *
 * 所有评审器必须实现此接口
 */
public interface Reviewer<T> {

    /**
     * 评审指定对象
     *
     * @param target 要评审的对象
     * @return 评审结果
     */
    ReviewResult review(T target);

    /**
     * 获取评审器类型
     */
    String getType();
}
