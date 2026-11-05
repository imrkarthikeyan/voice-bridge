package com.voicebridge.service.impl;

import com.voicebridge.dto.response.TimelineEventResponse;
import com.voicebridge.entity.*;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.repository.*;
import com.voicebridge.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TimelineServiceImpl implements TimelineService {

    private final MeetingRepository meetings;
    private final SlideVisitRepository visits;
    private final QuestionRepository questions;
    private final TimelineEventRepository events;

    @Override
    @Transactional(readOnly = true)
    public List<TimelineEventResponse> getTimeline(String code, Long organizerId, String token) {
        Meeting meeting = meetings.findByMeetingCode(code).orElseThrow(() -> new ResourceNotFoundException("Meeting not found"));
        boolean allowed = organizerId != null && meeting.getOrganizer().getId().equals(organizerId);
        if (!allowed) {
            throw new ResourceNotFoundException("Meeting not found");
        }
        List<TimelineEventResponse> result = new ArrayList<>();
        visits.findByMeetingIdOrderByEnteredMsAsc(meeting.getId()).forEach(v -> result.add(TimelineEventResponse.builder()
                .id(v.getId()).type("SLIDE").occurredMs(v.getEnteredMs()).slideNumber(v.getSlideNumber())
                .title("Slide " + v.getSlideNumber()).details(v.getLeftMs() == null ? "Currently shown" : "Displayed for " + Duration.ofMillis(v.getLeftMs() - v.getEnteredMs()).toSeconds() + " seconds").build()));
        questions.findByMeetingIdOrderByCreatedAtAsc(meeting.getId()).forEach(q -> result.add(TimelineEventResponse.builder()
                .id(q.getId()).type("QUESTION").occurredMs(Math.max(0, q.getCreatedAt().toEpochMilli() - meeting.getCreatedAt().toEpochMilli()))
                .slideNumber(q.getSlideNumber()).title("Audience question").details(q.getText()).build()));
        result.sort(Comparator.comparingLong(TimelineEventResponse::getOccurredMs));
        return result;
    }
}
