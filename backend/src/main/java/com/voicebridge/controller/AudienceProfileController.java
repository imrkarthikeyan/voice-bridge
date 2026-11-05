package com.voicebridge.controller;

import com.voicebridge.dto.request.UpdateAudienceProfileRequest;
import com.voicebridge.dto.response.AudienceProfileResponse;
import com.voicebridge.exception.InvalidCredentialsException;
import com.voicebridge.security.OrganizerPrincipal;
import com.voicebridge.security.AudiencePrincipal;
import com.voicebridge.service.AudienceProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AudienceProfileController {
    private final AudienceProfileService profileService;

    @PostMapping("/api/audience/profile/share")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void shareProfile(@AuthenticationPrincipal AudiencePrincipal audience,
                             @RequestHeader("X-Participant-Token") String token,
                             @RequestParam(defaultValue = "FULL") String mode) {
        profileService.shareForParticipant(audience.getId(), token, mode);
    }

    @GetMapping("/api/participants/profile")
    public AudienceProfileResponse getProfile(
            @RequestHeader(value = "X-Participant-Token", required = false) String token,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken) {
        return profileService.getForParticipant(token != null ? token : sessionToken);
    }

    @PutMapping("/api/participants/profile")
    public AudienceProfileResponse updateProfile(
            @RequestHeader(value = "X-Participant-Token", required = false) String token,
            @RequestHeader(value = "X-Session-Token", required = false) String sessionToken,
            @Valid @RequestBody UpdateAudienceProfileRequest request) {
        return profileService.updateForParticipant(token != null ? token : sessionToken, request);
    }

    @GetMapping("/api/meetings/participants/{participantId}/profile")
    public AudienceProfileResponse getOrganizerProfile(
            @AuthenticationPrincipal OrganizerPrincipal organizer,
            @PathVariable Long participantId) {
        if (organizer == null) {
            throw new InvalidCredentialsException("Organizer authentication required");
        }
        return profileService.getForOrganizer(organizer.getId(), participantId);
    }
}
