package com.voicebridge.controller;

import com.voicebridge.dto.request.ReviewQuestionRequest;
import com.voicebridge.dto.response.*;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.service.MeetingIntelligenceService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings/{meetingCode}/intelligence")
@RequiredArgsConstructor
public class MeetingIntelligenceController {

    private final MeetingIntelligenceService service;

    @GetMapping("/context")
    public MeetingContextResponse context(@PathVariable String meetingCode,
            @AuthenticationPrincipal OrganizerPrincipal organizer) {
        return service.getContext(meetingCode, organizer == null ? null : organizer.getId());
    }

    @GetMapping("/questions/analytics")
    public QuestionAnalyticsResponse analytics(@PathVariable String meetingCode,
            @AuthenticationPrincipal OrganizerPrincipal organizer) {
        return service.getQuestionAnalytics(meetingCode, organizer == null ? null : organizer.getId());
    }

    @PatchMapping("/questions/{questionId}")
    public QuestionResponse review(@PathVariable String meetingCode, @PathVariable Long questionId,
            @AuthenticationPrincipal OrganizerPrincipal organizer,
            @Valid @RequestBody ReviewQuestionRequest request) {
        return service.reviewQuestion(meetingCode, organizer == null ? null : organizer.getId(), questionId, request);
    }
}
