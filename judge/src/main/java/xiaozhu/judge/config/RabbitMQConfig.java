package xiaozhu.judge.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.ExchangeBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xiaozhu.common.constant.RabbitMQConstants;

import static xiaozhu.common.constant.RabbitMQConstants.*;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/17 15:59
 */

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange judgeExchange() {
        return ExchangeBuilder.directExchange(RabbitMQConstants.JUDGE_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue judgeQueue() {
        return QueueBuilder.durable(RabbitMQConstants.JUDGE_QUEUE).build();
    }

    @Bean
    public Binding judgeBinding() {
        return BindingBuilder.bind(judgeQueue())
                .to(judgeExchange())
                .with(RabbitMQConstants.JUDGE_ROUTING_KEY);
    }

    // 结果队列相关配置
    @Bean
    public DirectExchange resultExchange() {
        return ExchangeBuilder.directExchange(RabbitMQConstants.RESULT_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue resultQueue() {
        return QueueBuilder.durable(RabbitMQConstants.RESULT_QUEUE)
                .withArgument("x-message-ttl", 600000)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(resultQueue())
                .to(resultExchange())
                .with(RabbitMQConstants.RESULT_ROUTING_KEY);
    }

    // 死信队列配置
    @Bean
    public DirectExchange dlxExchange() {
        return ExchangeBuilder.directExchange(DLX_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean
    public Queue dlxQueue() {
        return QueueBuilder.durable(DLX_QUEUE).build();
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }

    @Bean
    public Declarables declarables() {
        return new Declarables(
                judgeExchange(),
                judgeQueue(),
                judgeBinding(),
                resultExchange(),
                resultQueue(),
                resultBinding(),
                dlxExchange(),
                dlxQueue(),
                dlxBinding()
        );
    }
}
