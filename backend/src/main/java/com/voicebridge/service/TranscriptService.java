package com.voicebridge.service;

import com.voicebridge.dto.response.TranscriptSegmentResponse;
import com.voicebridge.webrtc.SignalMessage;

import java.util.List;

public interface TranscriptService {

    TranscriptSegmentResponse recordSignal(String meetingCode, SignalMessage signal);

    List<TranscriptSegmentResponse> listForMeeting(String meetingCode, Long organizerId, String participantToken);
}
