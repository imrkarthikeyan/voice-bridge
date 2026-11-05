package com.voicebridge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import com.voicebridge.websocket.AiAudioRelayHandler;
import com.voicebridge.websocket.AiAudioHandshakeInterceptor;
import org.springframework.boot.web.servlet.ServletContextInitializer;
import jakarta.websocket.server.ServerContainer;

@Configuration
@EnableWebSocketMessageBroker
@EnableWebSocket
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer, WebSocketConfigurer {

    private final com.voicebridge.security.StompAuthenticationInterceptor stompAuthenticationInterceptor;
    private final AiAudioRelayHandler aiAudioRelayHandler;
    private final AiAudioHandshakeInterceptor aiAudioHandshakeInterceptor;

    public WebSocketConfig(com.voicebridge.security.StompAuthenticationInterceptor stompAuthenticationInterceptor,
                           AiAudioRelayHandler aiAudioRelayHandler,
                           AiAudioHandshakeInterceptor aiAudioHandshakeInterceptor) {
        this.stompAuthenticationInterceptor = stompAuthenticationInterceptor;
        this.aiAudioRelayHandler = aiAudioRelayHandler;
        this.aiAudioHandshakeInterceptor = aiAudioHandshakeInterceptor;
    }

    @Value("${app.cors.allowed-origins:*}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(
            org.springframework.messaging.simp.config.ChannelRegistration registration) {
        registration.interceptors(stompAuthenticationInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        String[] patterns = allowedOrigins.equals("*")
                ? new String[]{"*"}
                : allowedOrigins.split(",");

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(patterns)
                .withSockJS();

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(patterns);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(aiAudioRelayHandler, "/ai-ws")
                .addInterceptors(aiAudioHandshakeInterceptor)
                .setAllowedOriginPatterns(allowedOrigins.equals("*")
                        ? new String[]{"*"} : allowedOrigins.split(","));
    }

    @org.springframework.context.annotation.Bean
    public ServletContextInitializer webSocketContainerInitializer() {
        return servletContext -> {
            Object attribute = servletContext.getAttribute(ServerContainer.class.getName());
            if (attribute instanceof ServerContainer container) {
                container.setDefaultMaxBinaryMessageBufferSize(1024 * 1024);
                container.setDefaultMaxTextMessageBufferSize(64 * 1024);
                container.setDefaultMaxSessionIdleTimeout(120_000L);
            }
        };
    }
}
