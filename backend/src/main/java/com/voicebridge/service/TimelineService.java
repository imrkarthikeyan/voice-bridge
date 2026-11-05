package com.voicebridge.service;

import com.voicebridge.dto.response.TimelineEventResponse;
import java.util.List;

public interface TimelineService {

    List<TimelineEventResponse> getTimeline(String meetingCode, Long organizerId, String participantToken);
}
