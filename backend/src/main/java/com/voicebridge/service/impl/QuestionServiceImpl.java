package com.voicebridge.service.impl;

import com.voicebridge.dto.request.CreateQuestionRequest;
import com.voicebridge.dto.response.QuestionResponse;
import com.voicebridge.entity.*;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.repository.*;
import com.voicebridge.service.QuestionService;
import com.voicebridge.service.QuestionClassificationService;
import com.voicebridge.websocket.MeetingEventPublisher;
import com.voicebridge.websocket.MeetingEventType;
import com.voicebridge.service.SlideAlignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QuestionServiceImpl implements QuestionService {

    private static final Pattern QUESTION = Pattern.compile("\\?|\\b(why|how|what|when|where|can|could|should|is|are|எப்படி|ஏன்|என்ன)\\b", Pattern.CASE_INSENSITIVE);
    private final MeetingRepository meetings;
    private final ParticipantRepository participants;
    private final QuestionRepository questions;
    private final SlideRepository slides;
    private final QuestionSlideLinkRepository links;
    private final SlideAlignmentService slideAlignmentService;
    private final com.voicebridge.repository.TranscriptSegmentRepository transcripts;
    private final QuestionClassificationService classifier;
    private final MeetingEventPublisher eventPublisher;

    @Override
    @Transactional
    public QuestionResponse create(String code, String token, CreateQuestionRequest request) {
        Meeting meeting = meetings.findByMeetingCode(code).orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        Participant participant = participants.findBySessionToken(token).filter(Participant::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Participant session not found"));
        if (!participant.getMeeting().getId().equals(meeting.getId())) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        String text = request.getText().trim();
        QuestionClassificationService.Classification classification
                = classifier.classify(text, request.getLanguage());
        String type = classification.getType();
        List<Question> existing = questions.findByMeetingIdOrderByCreatedAtAsc(meeting.getId());
        Question duplicateOf = existing.stream()
                .filter(q -> !"DISMISSED".equals(q.getStatus()) && score(text, q.getText()) >= 0.7)
                .max(Comparator.comparing(Question::getCreatedAt))
                .orElse(null);
        int priority = priorityScore(text, type);
        if (!classification.isQuestion()) {
            throw new IllegalArgumentException("Submitted text must contain a question");
        }
        Question question = questions.save(Question.builder().meeting(meeting).participant(participant).text(text)
                .originalText(request.getOriginalText()).language(classification.getLanguage())
                .translatedText(classification.getTranslatedText()).source("TYPED").type(type)
                .status(duplicateOf == null ? "OPEN" : "DUPLICATE")
                .duplicateOf(duplicateOf).priorityScore(priority)
                .questionConfidence(classification.getConfidence())
                .slideNumber(slideAlignmentService.slideAt(meeting, null))
                .priorityReason(priorityReason(text, type, duplicateOf)).build());
        List<Slide> candidates = slides.findByPresentationMeetingIdOrderByPresentationIdAscSlideNumberAsc(meeting.getId());
        if (!candidates.isEmpty()) {
            Slide best = candidates.stream().max(Comparator.comparingDouble(s -> score(text, s.getExtractedText()))).orElseThrow();
            double score = score(text, best.getExtractedText());
            if (question.getSlideNumber() == null && score > 0) {
                question.setSlideNumber(best.getSlideNumber());
                questions.save(question);
                links.save(QuestionSlideLink.builder().question(question).slide(best).score(score).build());
            }
        }
        QuestionResponse response = toResponse(question);
        eventPublisher.publish(meeting.getMeetingCode(), MeetingEventType.QUESTION_CAPTURED, response);
        return response;
    }

    @Override
    @Transactional
    public Optional<QuestionResponse> captureSpeakerTurn(SpeakingRequest request) {
        Long startMs = request.getStartedAt() == null ? null
                : slideAlignmentService.elapsedMs(request.getMeeting(), request.getStartedAt());
        Long endMs = slideAlignmentService.elapsedMs(request.getMeeting(), Instant.now());
        if (startMs == null) {
            startMs = request.getApprovedAt() == null ? 0L
                    : slideAlignmentService.elapsedMs(request.getMeeting(), request.getApprovedAt());
        }
        List<TranscriptSegment> rows = transcripts
                .findByMeetingIdAndParticipantIdAndStartMsGreaterThanEqualAndStartMsLessThanEqualAndPartialFalseOrderByStartMsAscIdAsc(
                        request.getMeeting().getId(), request.getParticipant().getId(), startMs, endMs);
        Map<String, TranscriptSegment> latest = rows.stream().collect(Collectors.toMap(
                TranscriptSegment::getSegmentId, segment -> segment,
                (first, second) -> second.getRevision() >= first.getRevision() ? second : first,
                LinkedHashMap::new));
        List<TranscriptSegment> finalRows = latest.values().stream().sorted(Comparator
                .comparing(TranscriptSegment::getStartMs, Comparator.nullsFirst(Long::compareTo))
                .thenComparing(TranscriptSegment::getId)).toList();
        String text = finalRows.stream().map(TranscriptSegment::getText).filter(Objects::nonNull)
                .map(String::trim).filter(value -> !value.isBlank()).collect(Collectors.joining(" ")).trim();
        if (text.isBlank()) {
            return Optional.empty();
        }
        String language = finalRows.stream().map(TranscriptSegment::getLanguage)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("und");
        QuestionClassificationService.Classification classification = classifier.classify(text, language);
        if (!classification.isQuestion()) {
            return Optional.empty();
        }
        Question question = questions.save(Question.builder().meeting(request.getMeeting())
                .participant(request.getParticipant()).text(text).originalText(text)
                .language(classification.getLanguage()).translatedText(classification.getTranslatedText())
                .source("SPEECH").type(classification.getType()).status("OPEN")
                .priorityScore(priorityScore(text, classification.getType()))
                .priorityReason(priorityReason(text, classification.getType(), null))
                .questionConfidence(classification.getConfidence())
                .startMs(startMs).endMs(endMs)
                .slideNumber(slideAlignmentService.slideAt(request.getMeeting(), endMs)).build());
        QuestionResponse response = toResponse(question);
        eventPublisher.publish(request.getMeeting().getMeetingCode(), MeetingEventType.QUESTION_CAPTURED, response);
        return Optional.of(response);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuestionResponse> list(String code, Long organizerId, String token) {
        Meeting meeting = meetings.findByMeetingCode(code).orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        boolean allowed = organizerId != null && meeting.getOrganizer().getId().equals(organizerId)
                || token != null && participants.findBySessionToken(token).map(p -> p.getMeeting().getId().equals(meeting.getId())).orElse(false);
        if (!allowed) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return questions.findByMeetingIdOrderByCreatedAtAsc(meeting.getId()).stream().map(this::toResponse).toList();
    }

    private String classify(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("why") || lower.contains("ஏன்")) {
            return "DESIGN_RATIONALE";
        }
        if (lower.contains("how") || lower.contains("எப்படி")) {
            return "IMPLEMENTATION";
        }
        if (lower.contains("when") || lower.contains("where")) {
            return "LOGISTICS";
        }
        return QUESTION.matcher(text).find() ? "CLARIFICATION" : "COMMENT";
    }

    private double score(String question, String slide) {
        Set<String> q = words(question), s = words(slide);
        if (q.isEmpty() || s.isEmpty()) {
            return 0;
        }
        return q.stream().filter(s::contains).distinct().count() / (double) q.size();
    }

    private Set<String> words(String text) {
        Set<String> out = new HashSet<>();
        var m = Pattern.compile("[\\p{L}\\p{N}]{3,}").matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            out.add(m.group());
        
        }return out;
    }

    private QuestionResponse toResponse(Question q) {
        return QuestionResponse.builder().id(q.getId())
                .participantId(q.getParticipant().getId()).participantName(q.getParticipant().getName()).text(q.getText())
                .originalText(q.getOriginalText()).language(q.getLanguage()).translatedText(q.getTranslatedText())
                .source(q.getSource()).type(q.getType()).status(q.getStatus()).slideNumber(q.getSlideNumber())
                .duplicateOfId(q.getDuplicateOf() == null ? null : q.getDuplicateOf().getId())
                .priorityScore(q.getPriorityScore()).priorityReason(q.getPriorityReason())
                .startMs(q.getStartMs()).endMs(q.getEndMs()).questionConfidence(q.getQuestionConfidence())
                .createdAt(q.getCreatedAt()).build();
    }

    private int priorityScore(String text, String type) {
        String lower = text.toLowerCase(Locale.ROOT);
        int score = switch (type) {
            case "DESIGN_RATIONALE", "IMPLEMENTATION" ->
                60;
            case "CLARIFICATION" ->
                45;
            default ->
                25;
        };
        if (lower.contains("urgent") || lower.contains("安全") || lower.contains("சிக்கல்")) {
            score += 25;
        }
        if (text.length() > 180) {
            score += 5;
        }
        return Math.min(100, score);
    }

    private String priorityReason(String text, String type, Question duplicateOf) {
        if (duplicateOf != null) {
            return "Similar to question " + duplicateOf.getId();
        }
        return switch (type) {
            case "DESIGN_RATIONALE" ->
                "Design decision requires presenter explanation";
            case "IMPLEMENTATION" ->
                "Implementation detail requested";
            case "CLARIFICATION" ->
                "Clarification requested by audience";
            default ->
                "General audience contribution";
        };
    }
}
