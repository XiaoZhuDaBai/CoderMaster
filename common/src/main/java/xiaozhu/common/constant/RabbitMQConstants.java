package xiaozhu.common.constant;

/**
 * RabbitMQ 常量声明，保证各服务之间命名一致
 */
public final class RabbitMQConstants {

    private RabbitMQConstants() {
    }

    // 判题任务
    public static final String JUDGE_EXCHANGE = "judge.exchange";
    public static final String JUDGE_QUEUE = "judge.queue";
    public static final String JUDGE_ROUTING_KEY = "judge.routing.key";

    // 判题结果
    public static final String RESULT_EXCHANGE = "result.exchange";
    public static final String RESULT_QUEUE = "result.queue";
    public static final String RESULT_ROUTING_KEY = "result.routing.key";

    // 题目生成事件
    public static final String PROBLEM_GENERATED_EXCHANGE = "problem.generated.exchange";
    public static final String PROBLEM_GENERATED_QUEUE = "problem.generated.queue";
    public static final String PROBLEM_GENERATED_ROUTING_KEY = "problem.generated.routing.key";
    public static final String PROBLEM_TESTCASE_GENERATED_QUEUE = "problem.generated.testcase.queue";

    // 测试用例同步到 MySQL
    public static final String TESTCASE_SYNC_EXCHANGE = "testcase.sync.exchange";
    public static final String TESTCASE_SYNC_QUEUE = "testcase.sync.queue";
    public static final String TESTCASE_SYNC_ROUTING_KEY = "testcase.sync.routing.key";

    // 死信队列配置
    public static final String DLX_EXCHANGE = "judge.dlx";
    public static final String DLX_QUEUE = "judge.dlx.queue";
    public static final String DLX_ROUTING_KEY = "judge.dlx.routing.key";
}

