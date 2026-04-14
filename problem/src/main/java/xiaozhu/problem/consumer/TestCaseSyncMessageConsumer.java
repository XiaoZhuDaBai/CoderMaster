package xiaozhu.problem.consumer;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import xiaozhu.common.constant.RabbitMQConstants;
import xiaozhu.common.message.TestCaseSyncMessage;
import xiaozhu.problem.service.testcase.TestCaseSyncService;

/**
 * 测试用例同步消息消费者
 * 负责监听 MQ 消息并委托给 TestCaseSyncService 处理
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TestCaseSyncMessageConsumer {

    private final TestCaseSyncService testCaseSyncService;

    @RabbitListener(queues = RabbitMQConstants.TESTCASE_SYNC_QUEUE)
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(TestCaseSyncMessage message) {
        log.info("收到测试用例同步消息，contentHash={}, userKey={}",
                message != null ? message.getContentHash() : "null",
                message != null ? message.getUserKey() : "null");
        testCaseSyncService.syncTestCases(message);
    }
}
