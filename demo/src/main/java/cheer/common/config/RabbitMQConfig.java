package cheer.common.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {
    // 交换机
    public static final String WELCOME_EXCHANGE = "exchange.welcome";
    // 队列
    public static final String WELCOME_QUEUE = "queue.welcome";
    // 路由键
    public static final String WELCOME_ROUTING_KEY = "welcome";
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
    @Bean
    public DirectExchange welcomeExchange() {
        return new DirectExchange(WELCOME_EXCHANGE);
    }

    @Bean
    public Queue welcomeQueue() {
        return new Queue(WELCOME_QUEUE, true);  // true = 持久化，RabbitMQ重启消息不丢
    }

    @Bean
    public Binding welcomeBinding(Queue welcomeQueue, DirectExchange welcomeExchange) {
        return BindingBuilder.bind(welcomeQueue).to(welcomeExchange).with(WELCOME_ROUTING_KEY);
    }
}
