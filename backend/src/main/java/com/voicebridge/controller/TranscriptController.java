package com.voicebridge.controller;

import com.voicebridge.dto.response.TranscriptSegmentResponse;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.service.TranscriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/meetings/{meetingCode}/transcript")
@RequiredArgsConstructor
public class TranscriptController {

    private final TranscriptService transcriptService;

    @GetMapping
    public ResponseEntity<List<TranscriptSegmentResponse>> listTranscript(
            @PathVariable String meetingCode,
            @AuthenticationPrincipal OrganizerPrincipal organizer,
            @RequestHeader(value = "X-Participant-Token", required = false) String participantToken) {
        Long organizerId = organizer == null ? null : organizer.getId();
        return ResponseEntity.ok(transcriptService.listForMeeting(meetingCode, organizerId, participantToken));
    }
}
