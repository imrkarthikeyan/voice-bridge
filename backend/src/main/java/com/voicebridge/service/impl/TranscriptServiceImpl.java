package com.voicebridge.service.impl;

import com.voicebridge.dto.response.TranscriptSegmentResponse;
import com.voicebridge.entity.Meeting;
import com.voicebridge.entity.Participant;
import com.voicebridge.entity.TranscriptSegment;
import com.voicebridge.entity.TranscriptTranslation;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.repository.MeetingRepository;
import com.voicebridge.repository.ParticipantRepository;
import com.voicebridge.repository.TranscriptSegmentRepository;
import com.voicebridge.repository.TranscriptTranslationRepository;
import com.voicebridge.service.TranscriptService;
import com.voicebridge.service.SlideAlignmentService;
import com.voicebridge.webrtc.SignalMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TranscriptServiceImpl implements TranscriptService {

    private final MeetingRepository meetingRepository;
    private final ParticipantRepository participantRepository;
    private final TranscriptSegmentRepository transcriptRepository;
    private final TranscriptTranslationRepository translationRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final SlideAlignmentService slideAlignmentService;

    @Override
    @Transactional
    public TranscriptSegmentResponse recordSignal(String meetingCode, SignalMessage signal) {
        Meeting meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        if (signal.getText() == null || signal.getText().isBlank()) {
            throw new IllegalArgumentException("Transcript text is required");
        }

        String segmentId = signal.getSegmentId() == null || signal.getSegmentId().isBlank()
                ? "segment-" + System.nanoTime() : signal.getSegmentId();
        Integer revision = signal.getRevision() == null ? 1 : signal.getRevision();
        Participant speaker = signal.getParticipantId() == null ? null
                : participantRepository.findById(signal.getParticipantId()).orElse(null);

        Long serverEventMs = slideAlignmentService.elapsedMs(meeting, java.time.Instant.now());
        TranscriptSegment entity = TranscriptSegment.builder()
                .meeting(meeting)
                .segmentId(segmentId)
                .revision(revision)
                .source(signal.getFrom() == null ? "UNKNOWN" : signal.getFrom().name())
                .participantId(signal.getParticipantId())
                .language(signal.getLanguage())
                .text(signal.getText().trim())
                .partial(!Boolean.TRUE.equals(signal.getIsFinal()))
                .startMs(serverEventMs)
                .endMs(serverEventMs)
                .confidence(signal.getConfidence())
                .slideNumber(slideAlignmentService.slideAt(meeting, serverEventMs))
                .build();
        entity = transcriptRepository.save(entity);

        if (Boolean.TRUE.equals(signal.getIsFinal()) && signal.getTranslation() instanceof java.util.Map<?, ?> translation) {
            Object text = translation.get("text");
            Object targetLanguage = translation.get("target_language");
            if (text instanceof String translatedText && targetLanguage instanceof String target
                    && !translatedText.isBlank() && !target.isBlank()) {
                translationRepository.save(TranscriptTranslation.builder()
                        .segment(entity)
                        .targetLanguage(target)
                        .text(translatedText)
                        .model(String.valueOf(translation.containsKey("model") ? translation.get("model") : "ai"))
                        .build());
            }
        }

        TranscriptSegmentResponse response = toResponse(entity, speaker == null ? signal.getSpeakerName() : speaker.getName());
        messagingTemplate.convertAndSend("/topic/meetings/" + meetingCode + "/subtitles",
                java.util.Map.of("type", entity.isPartial() ? "transcript.partial" : "transcript.final",
                        "payload", response));
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TranscriptSegmentResponse> listForMeeting(String meetingCode, Long organizerId, String participantToken) {
        Meeting meeting = meetingRepository.findByMeetingCode(meetingCode)
                .orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        boolean organizerAllowed = organizerId != null && meeting.getOrganizer().getId().equals(organizerId);
        boolean participantAllowed = participantToken != null && participantRepository.findBySessionToken(participantToken)
                .filter(Participant::isActive)
                .map(p -> p.getMeeting().getId().equals(meeting.getId()))
                .orElse(false);
        if (!organizerAllowed && !participantAllowed) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return transcriptRepository.findByMeetingIdOrderByCreatedAtAscIdAsc(meeting.getId()).stream()
                .map(segment -> toResponse(segment, null))
                .toList();
    }

    private TranscriptSegmentResponse toResponse(TranscriptSegment segment, String speakerName) {
        java.util.Map<String, String> translations = translationRepository.findBySegmentId(segment.getId()).stream()
                .collect(java.util.stream.Collectors.toMap(
                        TranscriptTranslation::getTargetLanguage,
                        TranscriptTranslation::getText,
                        (first, ignored) -> first));
        return TranscriptSegmentResponse.builder()
                .id(segment.getId()).segmentId(segment.getSegmentId()).revision(segment.getRevision())
                .source(segment.getSource()).participantId(segment.getParticipantId()).speakerName(speakerName)
                .language(segment.getLanguage()).text(segment.getText()).partial(segment.isPartial())
                .startMs(segment.getStartMs()).endMs(segment.getEndMs()).confidence(segment.getConfidence())
                .slideNumber(segment.getSlideNumber())
                .createdAt(segment.getCreatedAt()).translations(translations).build();
    }
}
