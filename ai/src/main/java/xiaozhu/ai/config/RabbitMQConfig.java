package xiaozhu.ai.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import xiaozhu.common.constant.RabbitMQConstants;

/**
 * RabbitMQ 基础配置，用于题目生成事件发布
 */
@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange problemGeneratedExchange() {
        return new DirectExchange(RabbitMQConstants.PROBLEM_GENERATED_EXCHANGE, true, false);
    }

    @Bean
    public Queue problemGeneratedQueue() {
        return QueueBuilder.durable(RabbitMQConstants.PROBLEM_GENERATED_QUEUE).build();
    }

    @Bean
    public Queue problemGeneratedTestCaseQueue() {
        return QueueBuilder.durable(RabbitMQConstants.PROBLEM_TESTCASE_GENERATED_QUEUE).build();
    }

    @Bean
    public Binding problemGeneratedBinding() {
        return BindingBuilder.bind(problemGeneratedQueue())
                .to(problemGeneratedExchange())
                .with(RabbitMQConstants.PROBLEM_GENERATED_ROUTING_KEY);
    }

    @Bean
    public Binding problemGeneratedTestCaseBinding() {
        return BindingBuilder.bind(problemGeneratedTestCaseQueue())
                .to(problemGeneratedExchange())
                .with(RabbitMQConstants.PROBLEM_GENERATED_ROUTING_KEY);
    }

    // 测试用例同步到 MySQL
    @Bean
    public DirectExchange testCaseSyncExchange() {
        return new DirectExchange(RabbitMQConstants.TESTCASE_SYNC_EXCHANGE, true, false);
    }

    @Bean
    public Queue testCaseSyncQueue() {
        return QueueBuilder.durable(RabbitMQConstants.TESTCASE_SYNC_QUEUE).build();
    }

    @Bean
    public Binding testCaseSyncBinding() {
        return BindingBuilder.bind(testCaseSyncQueue())
                .to(testCaseSyncExchange())
                .with(RabbitMQConstants.TESTCASE_SYNC_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jacksonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
                                         MessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}


