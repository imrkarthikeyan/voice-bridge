package com.voicebridge.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAudioRelayHandler extends AbstractWebSocketHandler {

    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final Map<String, Queue<WebSocketMessage<?>>> pendingMessages = new ConcurrentHashMap<>();

    @Value("${app.ai.websocket-url:ws://localhost:8090/ws/audio}")
    private String aiWebSocketUrl;

    @Override
    public void afterConnectionEstablished(WebSocketSession downstream) {
        pendingMessages.put(downstream.getId(), new ConcurrentLinkedQueue<>());
        client.execute(new UpstreamHandler(downstream), aiWebSocketUrl)
                .whenComplete((upstream, error) -> {
                    if (error != null) {
                        log.warn("AI audio relay connection failed", error);
                        close(downstream, CloseStatus.SERVER_ERROR);
                    }
                });
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession downstream, BinaryMessage message) {
        relay(downstream, message);
    }

    @Override
    protected void handleTextMessage(WebSocketSession downstream, TextMessage message) {
        relay(downstream, message);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession downstream, CloseStatus status) {
        close(upstream(downstream), status);
    }

    private void relay(WebSocketSession downstream, WebSocketMessage<?> message) {
        WebSocketSession upstream = upstream(downstream);
        if (upstream == null || !upstream.isOpen()) {
            Queue<WebSocketMessage<?>> pending = pendingMessages.get(downstream.getId());
            if (pending != null) {
                pending.add(message);
            }
            return;
        }
        try {
            upstream.sendMessage(message);
        } catch (Exception error) {
            log.warn("AI audio relay send failed", error);
            close(downstream, CloseStatus.SERVER_ERROR);
        }
    }

    private final Map<String, WebSocketSession> upstreams = new ConcurrentHashMap<>();

    private WebSocketSession upstream(WebSocketSession downstream) {
        return upstreams.get(downstream.getId());
    }

    private void flushPending(WebSocketSession downstream, WebSocketSession upstream) {
        Queue<WebSocketMessage<?>> pending = pendingMessages.remove(downstream.getId());
        if (pending == null) {
            return;
        }
        WebSocketMessage<?> message;
        while ((message = pending.poll()) != null) {
            try {
                upstream.sendMessage(message);
            } catch (Exception error) {
                log.warn("AI audio relay send failed", error);
                close(downstream, CloseStatus.SERVER_ERROR);
                return;
            }
        }
    }

    private void close(WebSocketSession session, CloseStatus status) {
        if (session == null || !session.isOpen()) return;
        try {
            session.close(status);
        } catch (Exception error) {
            log.debug("AI audio relay close failed", error);
        } finally {
            upstreams.remove(session.getId());
            pendingMessages.remove(session.getId());
            upstreams.values().removeIf(value -> value == session);
        }
    }

    private final class UpstreamHandler extends AbstractWebSocketHandler {
        private final WebSocketSession downstream;

        private UpstreamHandler(WebSocketSession downstream) {
            this.downstream = downstream;
        }

        @Override
        public void afterConnectionEstablished(WebSocketSession upstream) {
            upstreams.put(downstream.getId(), upstream);
            flushPending(downstream, upstream);
        }

        @Override
        protected void handleBinaryMessage(WebSocketSession upstream, BinaryMessage message) {
            relayToDownstream(message);
        }

        @Override
        protected void handleTextMessage(WebSocketSession upstream, TextMessage message) {
            relayToDownstream(message);
        }

        @Override
        public void afterConnectionClosed(WebSocketSession upstream, CloseStatus status) {
            upstreams.remove(downstream.getId());
            close(downstream, status);
        }

        private void relayToDownstream(WebSocketMessage<?> message) {
            if (!downstream.isOpen()) return;
            try {
                downstream.sendMessage(message);
            } catch (Exception error) {
                log.warn("AI audio relay response failed", error);
                close(downstream, CloseStatus.SERVER_ERROR);
            }
        }
    }
}
