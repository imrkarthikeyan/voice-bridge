package com.voicebridge.service;

import com.voicebridge.dto.request.UpdateAudienceProfileRequest;
import com.voicebridge.dto.response.AudienceProfileResponse;

public interface AudienceProfileService {
    AudienceProfileResponse getForParticipant(String sessionToken);
    AudienceProfileResponse updateForParticipant(String sessionToken, UpdateAudienceProfileRequest request);
    AudienceProfileResponse getForOrganizer(Long organizerId, Long participantId);
    void shareForParticipant(Long accountId, String participantToken, String mode);
}
