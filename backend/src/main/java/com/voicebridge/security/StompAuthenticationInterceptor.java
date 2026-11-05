package com.voicebridge.security;

import com.voicebridge.entity.Participant;
import com.voicebridge.repository.ParticipantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthenticationInterceptor implements ChannelInterceptor {

    private static final String AUTHORIZATION = "Authorization";
    private static final String PARTICIPANT_TOKEN = "X-Participant-Token";

    private final JwtService jwtService;
    private final OrganizerDetailsService organizerDetailsService;
    private final ParticipantRepository participantRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        String authorization = firstHeader(accessor, AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")) {
            authenticateOrganizer(accessor, authorization.substring("Bearer ".length()));
            return message;
        }

        String sessionToken = firstHeader(accessor, PARTICIPANT_TOKEN);
        if (sessionToken != null && !sessionToken.isBlank()) {
            Participant participant = participantRepository.findWithMeetingBySessionToken(sessionToken)
                    .filter(Participant::isActive)
                    .orElseThrow(() -> unauthorized("Invalid or inactive participant session"));
            accessor.setUser(new ParticipantStompPrincipal(
                    participant.getId(), participant.getMeeting().getMeetingCode()));
            return message;
        }

        throw unauthorized("STOMP connection requires organizer JWT or participant session token");
    }

    private void authenticateOrganizer(StompHeaderAccessor accessor, String token) {
        try {
            String email = jwtService.extractEmail(token);
            OrganizerPrincipal principal = (OrganizerPrincipal) organizerDetailsService.loadUserByUsername(email);
            if (!jwtService.isTokenValid(token, principal.getUsername())) {
                throw unauthorized("Invalid or expired organizer token");
            }
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    principal, null, principal.getAuthorities());
            accessor.setUser(authentication);
        } catch (MessagingException ex) {
            throw ex;
        } catch (Exception ex) {
            log.debug("Rejected invalid STOMP organizer token: {}", ex.getMessage());
            throw unauthorized("Invalid or expired organizer token");
        }
    }

    private String firstHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return values == null || values.isEmpty() ? null : values.get(0);
    }

    private MessagingException unauthorized(String message) {
        return new MessagingException(message);
    }
}
