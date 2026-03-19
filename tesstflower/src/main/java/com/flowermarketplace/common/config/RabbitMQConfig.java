package com.flowermarketplace.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cấu hình RabbitMQ.
 * Toàn bộ bean chỉ được tạo khi spring.rabbitmq.listener.simple.auto-startup=true
 * (mặc định là true, bị override thành false trong profile local).
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchange.notification:flower.notification.exchange}")
    private String notificationExchange;

    @Value("${rabbitmq.exchange.order:flower.order.exchange}")
    private String orderExchange;

    @Value("${rabbitmq.queue.email:flower.email.queue}")
    private String emailQueue;

    @Value("${rabbitmq.queue.order-status:flower.order.status.queue}")
    private String orderStatusQueue;

    @Value("${rabbitmq.queue.push-notification:flower.push.notification.queue}")
    private String pushNotificationQueue;

    @Value("${rabbitmq.routing-key.email:flower.email}")
    private String emailRoutingKey;

    @Value("${rabbitmq.routing-key.order-status:flower.order.status}")
    private String orderStatusRoutingKey;

    @Value("${rabbitmq.routing-key.push:flower.push}")
    private String pushRoutingKey;

    // ── Exchanges ──────────────────────────────────────────────────────

    @Bean
    public TopicExchange notificationExchange() {
        return new TopicExchange(notificationExchange);
    }

    @Bean
    public TopicExchange orderExchange() {
        return new TopicExchange(orderExchange);
    }

    // ── Queues ─────────────────────────────────────────────────────────

    @Bean
    public Queue emailQueue() {
        return QueueBuilder.durable(emailQueue).build();
    }

    @Bean
    public Queue orderStatusQueue() {
        return QueueBuilder.durable(orderStatusQueue).build();
    }

    @Bean
    public Queue pushNotificationQueue() {
        return QueueBuilder.durable(pushNotificationQueue).build();
    }

    // ── Bindings ───────────────────────────────────────────────────────

    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue())
                .to(notificationExchange())
                .with(emailRoutingKey);
    }

    @Bean
    public Binding orderStatusBinding() {
        return BindingBuilder.bind(orderStatusQueue())
                .to(orderExchange())
                .with(orderStatusRoutingKey);
    }

    @Bean
    public Binding pushNotificationBinding() {
        return BindingBuilder.bind(pushNotificationQueue())
                .to(notificationExchange())
                .with(pushRoutingKey);
    }

    // ── Template & Converter ───────────────────────────────────────────

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        // Không throw exception nếu RabbitMQ không có sẵn (fail-silently)
        template.setChannelTransacted(false);
        log.info("[RabbitMQ] RabbitTemplate đã khởi tạo");
        return template;
    }
}
