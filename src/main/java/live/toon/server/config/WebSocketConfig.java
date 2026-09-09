package live.toon.server.config;

import live.toon.server.security.JwtChannelInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtChannelInterceptor jwtChannelInterceptor;

    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker for room topics and user queues.
        // NOTE: user-scoped destinations (/user/queue/state) are resolved by Spring to
        // /queue/state-user{sessionId}, so the broker must handle the /queue prefix.
        //
        // Heartbeat (server↔client, every 10s): without it a client that loses network
        // uncleanly (wifi drop, laptop sleep) is never detected as disconnected — no
        // SessionDisconnectEvent fires, so the user stays "online" and their avatar stays
        // frozen in the room for everyone else until the OS-level TCP timeout kicks in
        // (can be hours). The heartbeat forces disconnect detection within ~10-20s.
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{10000, 10000})
                .setTaskScheduler(heartbeatScheduler());
        // Prefix for @MessageMapping methods
        registry.setApplicationDestinationPrefixes("/app");
        // Prefix for SimpMessagingTemplate.convertAndSendToUser()
        registry.setUserDestinationPrefix("/user");
    }

    @Bean
    public ThreadPoolTaskScheduler heartbeatScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("stomp-heartbeat-");
        scheduler.initialize();
        return scheduler;
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(jwtChannelInterceptor);
    }
}
