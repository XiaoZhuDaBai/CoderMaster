package xiaozhu.submission.config;

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

@Configuration
public class RabbitMQConfig {

    @Bean
    public DirectExchange judgeExchange() {
        return new DirectExchange(RabbitMQConstants.JUDGE_EXCHANGE, true, false);
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

    @Bean
    public DirectExchange resultExchange() {
        return new DirectExchange(RabbitMQConstants.RESULT_EXCHANGE, true, false);
    }

    @Bean
    public Queue resultQueue() {
        return QueueBuilder.durable(RabbitMQConstants.RESULT_QUEUE).build();
    }

    @Bean
    public Binding resultBinding() {
        return BindingBuilder.bind(resultQueue())
                .to(resultExchange())
                .with(RabbitMQConstants.RESULT_ROUTING_KEY);
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
