package xiaozhu.common.constant;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/16 2:02
 */
public class RedisKeyConstant {
    // 用户相关
    public static final String USER_TOKEN_PREFIX = "oj:user:token:";
    public static final String USER_VERIFY_CODE_PREFIX = "oj:user:verify:";

    // 题目相关
    public static final String QUESTION_PREFIX = "oj:question:";
    public static final String QUESTION_USER_INDEX_PREFIX = "oj:question:index:";
    public static final String QUESTION_TEST_CASE_PREFIX = "oj:question:testcases:";
    public static final String QUESTION_DELIVERY_PREFIX = "oj:question:delivery:";

    // 提交相关


    // 竞赛相关
    public static final String CONTEST_ALL = "oj:contest:all";

    // 限流相关
    public static final String RATE_LIMIT_PREFIX = "rate_limit:";
}
