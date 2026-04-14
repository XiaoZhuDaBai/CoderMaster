package xiaozhu.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * RabbitMQ 消息队列指标收集器
 * 用于收集消息队列的使用情况
 */
@Slf4j
@Service
public class RabbitMQMetricsService {

    private final MeterRegistry meterRegistry;

    public RabbitMQMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * 记录消息发布
     */
    public void recordMessagePublished(String exchange, String routingKey) {
        Counter.builder("rabbitmq.messages.published")
                .description("RabbitMQ 消息发布总数")
                .tag("exchange", exchange)
                .tag("routing_key", routingKey)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录消息消费
     */
    public void recordMessageConsumed(String queue) {
        Counter.builder("rabbitmq.messages.consumed")
                .description("RabbitMQ 消息消费总数")
                .tag("queue", queue)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录消息处理错误
     */
    public void recordMessageError(String queue) {
        Counter.builder("rabbitmq.messages.errors")
                .description("RabbitMQ 消息处理错误总数")
                .tag("queue", queue)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录消息处理时间
     */
    public void recordMessageProcessingTime(String queue, long durationMs) {
        Timer.builder("rabbitmq.messages.processing.duration")
                .description("RabbitMQ 消息处理时间")
                .tag("queue", queue)
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}

