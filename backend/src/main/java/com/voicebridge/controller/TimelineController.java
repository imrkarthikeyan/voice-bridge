package com.voicebridge.controller;

import com.voicebridge.dto.response.TimelineEventResponse;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.service.TimelineService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/meetings/{meetingCode}/timeline")
@RequiredArgsConstructor
public class TimelineController {

    private final TimelineService service;

    @GetMapping
    public List<TimelineEventResponse> get(@PathVariable String meetingCode, @AuthenticationPrincipal OrganizerPrincipal organizer) {
        return service.getTimeline(meetingCode, organizer == null ? null : organizer.getId(), null);
    }
}
