package com.artagram.canvas.usuario;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CanvasUserEventsRabbitMqConfig {

    public static final String EXCHANGE = "artagram.events";
    public static final String DLX_EXCHANGE = "artagram.dlx";

    public static final String QUEUE_USER_REGISTERED = "canvas.user.registered";
    public static final String QUEUE_USER_DELETED = "canvas.user.deleted";
    public static final String QUEUE_SUBSCRIPTION_UPDATED = "canvas.subscription.updated";

    @Bean
    public TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public Queue canvasUserRegisteredQueue() {
        return QueueBuilder.durable(QUEUE_USER_REGISTERED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_USER_REGISTERED + ".dlq")
                .build();
    }

    @Bean
    public Binding canvasUserRegisteredBinding(Queue canvasUserRegisteredQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(canvasUserRegisteredQueue).to(eventsExchange).with("user.registered");
    }

    @Bean
    public Queue canvasUserDeletedQueue() {
        return QueueBuilder.durable(QUEUE_USER_DELETED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_USER_DELETED + ".dlq")
                .build();
    }

    @Bean
    public Binding canvasUserDeletedBinding(Queue canvasUserDeletedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(canvasUserDeletedQueue).to(eventsExchange).with("user.deleted");
    }

    @Bean
    public Queue canvasSubscriptionUpdatedQueue() {
        return QueueBuilder.durable(QUEUE_SUBSCRIPTION_UPDATED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_SUBSCRIPTION_UPDATED + ".dlq")
                .build();
    }

    @Bean
    public Binding canvasSubscriptionUpdatedBinding(Queue canvasSubscriptionUpdatedQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(canvasSubscriptionUpdatedQueue).to(eventsExchange).with("subscription.updated");
    }
}
