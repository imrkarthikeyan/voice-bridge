package com.voicebridge.controller;

import com.voicebridge.dto.response.MeetingDocumentResponse;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.service.MeetingDocumentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/meetings/{meetingCode}/document")
@RequiredArgsConstructor
public class MeetingDocumentController {

    private final MeetingDocumentService service;

    @PostMapping("/generate")
    public ResponseEntity<MeetingDocumentResponse> generate(
            @AuthenticationPrincipal OrganizerPrincipal principal,
            @PathVariable String meetingCode) {
        return ResponseEntity.ok(service.generate(principal.getId(), meetingCode));
    }

    @GetMapping
    public ResponseEntity<MeetingDocumentResponse> get(
            @AuthenticationPrincipal OrganizerPrincipal principal,
            @PathVariable String meetingCode) {
        return ResponseEntity.ok(service.get(principal.getId(), meetingCode));
    }

    @PostMapping("/review")
    public ResponseEntity<MeetingDocumentResponse> review(
            @AuthenticationPrincipal OrganizerPrincipal principal,
            @PathVariable String meetingCode) {
        return ResponseEntity.ok(service.review(principal.getId(), meetingCode));
    }

    @PostMapping("/publish")
    public ResponseEntity<MeetingDocumentResponse> publish(
            @AuthenticationPrincipal OrganizerPrincipal principal,
            @PathVariable String meetingCode) {
        return ResponseEntity.ok(service.publish(principal.getId(), meetingCode));
    }

    @GetMapping("/download")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal OrganizerPrincipal principal,
            @PathVariable String meetingCode) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename("voicebridge-" + meetingCode + "-meeting-document.pdf")
                .build());
        return ResponseEntity.ok().headers(headers).body(service.download(principal.getId(), meetingCode));
    }
}
