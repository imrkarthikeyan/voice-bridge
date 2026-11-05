package com.voicebridge.service.impl;

import com.voicebridge.dto.request.ReviewQuestionRequest;
import com.voicebridge.dto.response.*;
import com.voicebridge.entity.*;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.repository.*;
import com.voicebridge.service.MeetingIntelligenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MeetingIntelligenceServiceImpl implements MeetingIntelligenceService {

    private final MeetingRepository meetings;
    private final QuestionRepository questions;
    private final TranscriptSegmentRepository transcripts;
    private final PresentationSessionRepository sessions;
    private final SlideRepository slides;

    @Override
    @Transactional(readOnly = true)
    public MeetingContextResponse getContext(String code, Long organizerId) {
        Meeting meeting = organizerMeeting(code, organizerId);
        PresentationSession session = sessions.findByMeetingId(meeting.getId()).orElse(null);
        Integer currentSlide = session == null ? null : session.getCurrentSlide();
        String presentationTitle = session == null ? null : session.getPresentation().getFileName();
        String slideText = null;
        if (session != null) {
            slideText = slides.findByPresentationIdOrderBySlideNumberAsc(session.getPresentation().getId()).stream()
                    .filter(slide -> slide.getSlideNumber().equals(currentSlide))
                    .map(Slide::getExtractedText).findFirst().orElse(null);
        }
        List<TranscriptSegment> transcriptRows = transcripts.findByMeetingIdOrderByCreatedAtAscIdAsc(meeting.getId()).stream()
                .filter(segment -> !segment.isPartial()).toList();
        List<TranscriptSegmentResponse> recentTranscript = transcriptRows.stream()
                .skip(Math.max(0, transcriptRows.size() - 20)).map(this::transcriptResponse).toList();
        List<QuestionResponse> prioritized = questions.findByMeetingIdOrderByPriorityScoreDescCreatedAtAsc(meeting.getId())
                .stream().filter(question -> "OPEN".equals(question.getStatus()) || "SELECTED".equals(question.getStatus()))
                .limit(10).map(this::questionResponse).toList();
        long openCount = questions.countByMeetingIdAndStatusIn(meeting.getId(), List.of("OPEN", "SELECTED"));
        return MeetingContextResponse.builder().currentSlide(currentSlide).presentationTitle(presentationTitle)
                .currentSlideText(slideText).recentTranscript(recentTranscript)
                .prioritizedQuestions(prioritized).openQuestionCount(openCount).build();
    }

    @Override
    @Transactional
    public QuestionResponse reviewQuestion(String code, Long organizerId, Long questionId, ReviewQuestionRequest request) {
        Meeting meeting = organizerMeeting(code, organizerId);
        Question question = questions.findById(questionId).filter(q -> q.getMeeting().getId().equals(meeting.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Question not found"));
        question.setStatus(request.getStatus());
        return questionResponse(questions.save(question));
    }

    @Override
    @Transactional(readOnly = true)
    public QuestionAnalyticsResponse getQuestionAnalytics(String code, Long organizerId) {
        Meeting meeting = organizerMeeting(code, organizerId);
        List<Question> all = questions.findByMeetingIdOrderByCreatedAtAsc(meeting.getId());
        Map<String, Long> byType = all.stream().collect(java.util.stream.Collectors.groupingBy(
                Question::getType, TreeMap::new, java.util.stream.Collectors.counting()));
        Map<Integer, Long> bySlide = all.stream().filter(q -> q.getSlideNumber() != null)
                .collect(java.util.stream.Collectors.groupingBy(Question::getSlideNumber, TreeMap::new,
                        java.util.stream.Collectors.counting()));
        return QuestionAnalyticsResponse.builder().total(all.size())
                .open(countStatus(all, "OPEN")).selected(countStatus(all, "SELECTED"))
                .answered(countStatus(all, "ANSWERED")).dismissed(countStatus(all, "DISMISSED"))
                .duplicates(all.stream().filter(q -> q.getDuplicateOf() != null).count())
                .byType(byType).bySlide(bySlide).build();
    }

    private Meeting organizerMeeting(String code, Long organizerId) {
        Meeting meeting = meetings.findByMeetingCode(code).orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        if (organizerId == null || !meeting.getOrganizer().getId().equals(organizerId)) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        return meeting;
    }

    private long countStatus(List<Question> all, String status) {
        return all.stream().filter(q -> status.equals(q.getStatus())).count();
    }

    private QuestionResponse questionResponse(Question q) {
        return QuestionResponse.builder().id(q.getId()).participantId(q.getParticipant().getId())
                .participantName(q.getParticipant().getName()).text(q.getText()).originalText(q.getOriginalText())
                .type(q.getType()).status(q.getStatus()).slideNumber(q.getSlideNumber())
                .language(q.getLanguage()).translatedText(q.getTranslatedText()).source(q.getSource())
                .duplicateOfId(q.getDuplicateOf() == null ? null : q.getDuplicateOf().getId())
                .priorityScore(q.getPriorityScore()).priorityReason(q.getPriorityReason())
                .startMs(q.getStartMs()).endMs(q.getEndMs()).questionConfidence(q.getQuestionConfidence())
                .createdAt(q.getCreatedAt()).build();
    }

    private TranscriptSegmentResponse transcriptResponse(TranscriptSegment segment) {
        return TranscriptSegmentResponse.builder().id(segment.getId()).segmentId(segment.getSegmentId())
                .revision(segment.getRevision()).text(segment.getText()).partial(segment.isPartial())
                .language(segment.getLanguage()).startMs(segment.getStartMs()).endMs(segment.getEndMs())
                .confidence(segment.getConfidence()).createdAt(segment.getCreatedAt()).build();
    }
}
