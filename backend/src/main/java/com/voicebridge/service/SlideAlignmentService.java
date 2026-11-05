package com.voicebridge.service;

import com.voicebridge.entity.Meeting;
import com.voicebridge.entity.SlideVisit;
import com.voicebridge.repository.SlideVisitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SlideAlignmentService {

    private final SlideVisitRepository slideVisitRepository;

    @Value("${app.presentation.slide-alignment-grace-ms:1500}")
    private long graceMs = 1500;

    public Integer slideAt(Meeting meeting, Long eventMs) {
        if (meeting.getStartedAt() == null) {
            return null;
        }
        long timestamp = eventMs == null ? elapsedMs(meeting, Instant.now()) : Math.max(0, eventMs);
        Integer alignedSlide = slideVisitRepository.findByMeetingIdOrderByEnteredMsAsc(meeting.getId()).stream()
                .filter(visit -> visit.getEnteredMs() <= timestamp)
                .filter(visit -> visit.getLeftMs() == null || timestamp <= visit.getLeftMs() + graceMs)
                .max(Comparator.comparing(SlideVisit::getEnteredMs))
                .map(latest -> {
                    if (timestamp - latest.getEnteredMs() <= graceMs) {
                        return slideVisitRepository.findByMeetingIdOrderByEnteredMsAsc(meeting.getId()).stream()
                                .filter(visit -> visit.getEnteredMs() < latest.getEnteredMs())
                                .max(Comparator.comparing(SlideVisit::getEnteredMs))
                                .map(SlideVisit::getSlideNumber)
                                .orElse(latest.getSlideNumber());
                    }
                    return latest.getSlideNumber();
                }).orElse(null);
        return alignedSlide;
    }

    public long elapsedMs(Meeting meeting, Instant timestamp) {
        return meeting.getStartedAt() == null ? 0 : Math.max(0,
                Duration.between(meeting.getStartedAt(), timestamp).toMillis());
    }

    public long elapsedMs(Meeting meeting) {
        return elapsedMs(meeting, Instant.now());
    }
}
