package com.voicebridge.webrtc;

import com.voicebridge.entity.Meeting;
import com.voicebridge.entity.enums.MeetingStatus;
import com.voicebridge.repository.MeetingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.util.Optional;
import java.util.Set;
import java.security.Principal;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.security.ParticipantStompPrincipal;
import org.springframework.security.core.Authentication;
import com.voicebridge.service.TranscriptService;

/**
 * Relays WebRTC SDP offers/answers and ICE candidates between the organizer's
 * browser and the currently approved speaker's browser. The server never
 * inspects or stores media — it only forwards signaling payloads so the actual
 * audio stream can be established peer-to-peer.
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebRtcSignalingController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MeetingRepository meetingRepository;
    private final TranscriptService transcriptService;

    private static final Set<String> ALLOWED_TYPES = Set.of("OFFER", "ANSWER", "ICE_CANDIDATE", "TRANSCRIPT");
    private static final Set<String> ALLOWED_ROLES = Set.of("ORGANIZER", "SPEAKER");

    public void relaySignal(@DestinationVariable String meetingCode, SignalMessage message) {
        relaySignalInternal(meetingCode, message, null);
    }

    @MessageMapping("/meetings/{meetingCode}/signal")
    public void relayAuthenticatedSignal(@DestinationVariable String meetingCode,
            SignalMessage message,
            Principal principal) {
        relaySignalInternal(meetingCode, message, principal);
    }

    private void relaySignalInternal(String meetingCode, SignalMessage message, Principal principal) {
        if (message == null || message.getType() == null || message.getFrom() == null) {
            log.warn("Dropping invalid/null WebRTC signal for meeting {}", meetingCode);
            return;
        }

        String typeStr = message.getType().name();
        String fromStr = message.getFrom().name();

        if (!ALLOWED_TYPES.contains(typeStr) || !ALLOWED_ROLES.contains(fromStr)) {
            log.warn("Dropping unauthorized signal type {} or role {} for meeting {}", typeStr, fromStr, meetingCode);
            return;
        }

        Optional<Meeting> meeting = principal == null
                ? meetingRepository.findByMeetingCode(meetingCode)
                : meetingRepository.findWithOrganizerByMeetingCode(meetingCode);

        if (meeting.isEmpty() || meeting.get().getStatus() == MeetingStatus.CLOSED) {
            log.debug("Dropping signal for unknown/closed meeting: {}", meetingCode);
            return;
        }

        if (principal != null && !senderMatchesMeeting(principal, meeting.get(), message)) {
            log.warn("Dropping signal with mismatched authenticated sender for meeting {}", meetingCode);
            return;
        }

        if (message.getType() == SignalType.TRANSCRIPT && transcriptService != null) {
            transcriptService.recordSignal(meetingCode, message);
        }

        log.debug("Relaying {} signal from {} in meeting {}", typeStr, fromStr, meetingCode);
        messagingTemplate.convertAndSend("/topic/meetings/" + meetingCode + "/signal", message);
    }

    private boolean senderMatchesMeeting(Principal principal, Meeting meeting, SignalMessage message) {
        if (principal instanceof Authentication authentication) {
            Object authenticatedPrincipal = authentication.getPrincipal();
            if (authenticatedPrincipal instanceof OrganizerPrincipal organizer) {
                return organizerMatchesMeeting(organizer, meeting, message);
            }
            if (authenticatedPrincipal instanceof ParticipantStompPrincipal participant) {
                return participantMatchesMeeting(participant, meeting, message);
            }
            return false;
        }
        if (principal instanceof OrganizerPrincipal organizer) {
            return organizerMatchesMeeting(organizer, meeting, message);
        }
        if (principal instanceof ParticipantStompPrincipal participant) {
            return participantMatchesMeeting(participant, meeting, message);
        }
        return false;
    }

    private boolean organizerMatchesMeeting(OrganizerPrincipal organizer, Meeting meeting, SignalMessage message) {
        return message.getFrom() == SignalRole.ORGANIZER
                && meeting.getOrganizer().getId().equals(organizer.getId());
    }

    private boolean participantMatchesMeeting(ParticipantStompPrincipal participant,
            Meeting meeting,
            SignalMessage message) {
        return message.getFrom() == SignalRole.SPEAKER
                && meeting.getMeetingCode().equalsIgnoreCase(participant.meetingCode())
                && (message.getParticipantId() == null
                || participant.participantId().equals(message.getParticipantId()));
    }
}
