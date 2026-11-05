package com.voicebridge.service;

import com.voicebridge.dto.response.MeetingDocumentResponse;

public interface MeetingDocumentService {
    MeetingDocumentResponse generate(Long organizerId, String meetingCode);
    MeetingDocumentResponse get(Long organizerId, String meetingCode);
    MeetingDocumentResponse review(Long organizerId, String meetingCode);
    MeetingDocumentResponse publish(Long organizerId, String meetingCode);
    byte[] download(Long organizerId, String meetingCode);
}
