package com.artagram.canvas.feed;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Colas PROPIAS (no las "lienzo.*" que sigue usando ARTAGRAM-backv1): un topic
 * exchange reparte una copia independiente del mensaje a cada cola que se le
 * ligue, así que mientras backv1 y artagram-canvas convivan, cada uno con su
 * propia cola recibe y retransmite el evento sin competir por él ni robárselo
 * al otro.
 */
@Configuration
public class FeedEventsRabbitMqConfig {

    public static final String EXCHANGE = "artagram.events";
    public static final String DLX_EXCHANGE = "artagram.dlx";

    public static final String QUEUE_LIKE_UPDATED = "canvas.like.updated";
    public static final String QUEUE_COMMENT_CREATED = "canvas.comment.created";
    public static final String QUEUE_COMMENT_DELETED = "canvas.comment.deleted";

    // El TopicExchange "artagram.events" ya lo declara CanvasUserEventsRabbitMqConfig;
    // Spring inyecta ese mismo bean por tipo en los @Bean de binding de abajo.

    @Bean
    public Queue canvasLikeUpdatedQueue() {
        return QueueBuilder.durable(QUEUE_LIKE_UPDATED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_LIKE_UPDATED + ".dlq")
                .build();
    }

    @Bean
    public Binding canvasLikeUpdatedBinding(Queue canvasLikeUpdatedQueue, TopicExchange feedEventsExchange) {
        return BindingBuilder.bind(canvasLikeUpdatedQueue).to(feedEventsExchange).with("like.updated");
    }

    @Bean
    public Queue canvasCommentCreatedQueue() {
        return QueueBuilder.durable(QUEUE_COMMENT_CREATED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_COMMENT_CREATED + ".dlq")
                .build();
    }

    @Bean
    public Binding canvasCommentCreatedBinding(Queue canvasCommentCreatedQueue, TopicExchange feedEventsExchange) {
        return BindingBuilder.bind(canvasCommentCreatedQueue).to(feedEventsExchange).with("comment.created");
    }

    @Bean
    public Queue canvasCommentDeletedQueue() {
        return QueueBuilder.durable(QUEUE_COMMENT_DELETED)
                .deadLetterExchange(DLX_EXCHANGE)
                .deadLetterRoutingKey(QUEUE_COMMENT_DELETED + ".dlq")
                .build();
    }

    @Bean
    public Binding canvasCommentDeletedBinding(Queue canvasCommentDeletedQueue, TopicExchange feedEventsExchange) {
        return BindingBuilder.bind(canvasCommentDeletedQueue).to(feedEventsExchange).with("comment.deleted");
    }
}
