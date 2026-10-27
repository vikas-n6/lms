package com.lms.gateway.stream;

import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Subscribes the gateway to the Redis dashboard channel and relays each message to the SSE clients
 * held by {@link DashboardStreamController}. Services publish a lightweight "something changed"
 * ping on this channel (Kafka stays the source of truth); the gateway is the fan-out point.
 */
@Configuration
public class DashboardStreamConfig {

    @Bean
    public RedisMessageListenerContainer dashboardListenerContainer(
            RedisConnectionFactory connectionFactory,
            DashboardStreamController controller,
            @Value("${lms.dashboard.stream-channel:lms:dashboard:events}") String channel) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        MessageListener listener =
                (message, pattern) ->
                        controller.broadcast(new String(message.getBody(), StandardCharsets.UTF_8));
        container.addMessageListener(listener, new PatternTopic(channel));
        return container;
    }
}
