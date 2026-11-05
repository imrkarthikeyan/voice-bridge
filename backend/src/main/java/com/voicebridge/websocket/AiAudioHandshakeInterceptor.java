package com.voicebridge.websocket;

import com.voicebridge.entity.enums.MeetingStatus;
import com.voicebridge.entity.enums.SpeakingRequestStatus;
import com.voicebridge.repository.ParticipantRepository;
import com.voicebridge.repository.SpeakingRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class AiAudioHandshakeInterceptor implements HandshakeInterceptor {

    private static final String SESSION_TOKEN = "sessionToken";

    private final ParticipantRepository participantRepository;
    private final SpeakingRequestRepository speakingRequestRepository;

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {
        String token = queryParameter(request.getURI(), SESSION_TOKEN);
        if (token == null || token.isBlank()) {
            log.warn("Rejected AI audio WebSocket without participant session token");
            return false;
        }

        return participantRepository.findWithMeetingBySessionToken(token)
                .filter(participant -> participant.isActive())
                .filter(participant -> participant.getMeeting().getStatus() == MeetingStatus.ACTIVE)
                .filter(participant -> speakingRequestRepository
                        .findByParticipantIdAndStatusIn(
                                participant.getId(),
                                List.of(SpeakingRequestStatus.APPROVED, SpeakingRequestStatus.SPEAKING))
                        .isPresent())
                .map(participant -> {
                    attributes.put("participantId", participant.getId());
                    attributes.put("meetingId", participant.getMeeting().getId());
                    return true;
                })
                .orElseGet(() -> {
                    log.warn("Rejected AI audio WebSocket for invalid or inactive speaker session");
                    return false;
                });
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {
        if (exception != null) {
            log.warn("AI audio WebSocket handshake failed", exception);
        }
    }

    private String queryParameter(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null) {
            return null;
        }
        for (String entry : query.split("&")) {
            String[] parts = entry.split("=", 2);
            if (parts.length == 2 && name.equals(URLDecoder.decode(parts[0], StandardCharsets.UTF_8))) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }
}
