package com.voicebridge.service.impl;

import com.voicebridge.dto.request.UpdateAudienceProfileRequest;
import com.voicebridge.dto.response.AudienceProfileResponse;
import com.voicebridge.entity.AudienceProfile;
import com.voicebridge.entity.Participant;
import com.voicebridge.exception.ResourceNotFoundException;
import com.voicebridge.repository.AudienceProfileRepository;
import com.voicebridge.repository.ParticipantRepository;
import com.voicebridge.repository.ParticipantAccountLinkRepository;
import com.voicebridge.repository.ProfileShareSnapshotRepository;
import com.voicebridge.entity.ProfileShareSnapshot;
import com.voicebridge.service.AudienceProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AudienceProfileServiceImpl implements AudienceProfileService {
    private final AudienceProfileRepository profileRepository;
    private final ParticipantRepository participantRepository;
    private final ParticipantAccountLinkRepository linkRepository;
    private final ProfileShareSnapshotRepository snapshotRepository;

    @Override
    @Transactional
    public AudienceProfileResponse getForParticipant(String token) {
        return toResponse(profile(token));
    }

    @Override
    @Transactional
    public AudienceProfileResponse updateForParticipant(String token, UpdateAudienceProfileRequest request) {
        AudienceProfile profile = profile(token);
        if (request.getDisplayName() != null) profile.setDisplayName(request.getDisplayName().trim());
        if (request.getPhotoUrl() != null) profile.setPhotoUrl(request.getPhotoUrl().trim());
        if (request.getHeadline() != null) profile.setHeadline(request.getHeadline().trim());
        if (request.getBio() != null) profile.setBio(request.getBio().trim());
        if (request.getSkills() != null) profile.setSkills(request.getSkills().trim());
        if (request.getInterests() != null) profile.setInterests(request.getInterests().trim());
        if (request.getGithubUrl() != null) profile.setGithubUrl(request.getGithubUrl().trim());
        if (request.getLinkedinUrl() != null) profile.setLinkedinUrl(request.getLinkedinUrl().trim());
        if (request.getPortfolioUrl() != null) profile.setPortfolioUrl(request.getPortfolioUrl().trim());
        if (request.getPhotoVisibility() != null) profile.setPhotoVisibility(request.getPhotoVisibility());
        if (request.getBioVisibility() != null) profile.setBioVisibility(request.getBioVisibility());
        if (request.getSkillsVisibility() != null) profile.setSkillsVisibility(request.getSkillsVisibility());
        if (request.getLinksVisibility() != null) profile.setLinksVisibility(request.getLinksVisibility());
        if (request.getShareMode() != null && java.util.Set.of("NONE", "QUESTION_ONLY", "FULL").contains(request.getShareMode().trim().toUpperCase())) {
            profile.setShareMode(request.getShareMode().trim().toUpperCase());
        }
        return toResponse(profileRepository.save(profile));
    }

    @Override
    @Transactional(readOnly = true)
    public AudienceProfileResponse getForOrganizer(Long organizerId, Long participantId) {
        AudienceProfile profile = profileRepository.findByParticipantId(participantId)
                .filter(p -> p.getParticipant().getMeeting().getOrganizer().getId().equals(organizerId))
                .orElseThrow(() -> new ResourceNotFoundException("Audience profile not found"));
        return toOrganizerResponse(profile);
    }

    @Override
    @Transactional
    public void shareForParticipant(Long accountId, String participantToken, String mode) {
        Participant participant = participantRepository.findWithMeetingBySessionToken(participantToken)
                .orElseThrow(() -> new ResourceNotFoundException("Participant session not found"));
        linkRepository.findById(participant.getId())
                .filter(l -> l.getAccount().getId().equals(accountId))
                .orElseThrow(() -> new ResourceNotFoundException("Participant is not linked to this account"));
        String normalized = mode == null ? "FULL" : mode.trim().toUpperCase();
        if (!java.util.Set.of("QUESTION_ONLY", "FULL").contains(normalized)) {
            throw new IllegalArgumentException("Share mode must be FULL or QUESTION_ONLY");
        }
        AudienceProfile profile = profile(participant.getSessionToken());
        snapshotRepository.save(ProfileShareSnapshot.builder().meeting(participant.getMeeting())
                .participant(participant).account(linkRepository.findById(participant.getId()).orElseThrow().getAccount())
                .shareMode(normalized).payload(toResponse(profile).toString()).build());
    }

    private AudienceProfileResponse toOrganizerResponse(AudienceProfile p) {
        AudienceProfileResponse full = toResponse(p);
        return AudienceProfileResponse.builder().participantId(full.getParticipantId())
                .displayName(full.getDisplayName())
                .photoUrl(p.getPhotoVisibility() == com.voicebridge.entity.enums.ProfileVisibility.PRESENTER ? full.getPhotoUrl() : null)
                .headline(full.getHeadline())
                .bio(p.getBioVisibility() == com.voicebridge.entity.enums.ProfileVisibility.PRESENTER ? full.getBio() : null)
                .skills(p.getSkillsVisibility() == com.voicebridge.entity.enums.ProfileVisibility.PRESENTER ? full.getSkills() : null)
                .interests(full.getInterests()).githubUrl(p.getLinksVisibility() == com.voicebridge.entity.enums.ProfileVisibility.PRESENTER ? full.getGithubUrl() : null)
                .linkedinUrl(p.getLinksVisibility() == com.voicebridge.entity.enums.ProfileVisibility.PRESENTER ? full.getLinkedinUrl() : null)
                .portfolioUrl(p.getLinksVisibility() == com.voicebridge.entity.enums.ProfileVisibility.PRESENTER ? full.getPortfolioUrl() : null)
                .githubVerified(full.isGithubVerified()).photoVisibility(full.getPhotoVisibility()).bioVisibility(full.getBioVisibility())
                .skillsVisibility(full.getSkillsVisibility()).linksVisibility(full.getLinksVisibility()).shareMode(full.getShareMode()).build();
    }

    private AudienceProfile profile(String token) {
        Participant participant = participantRepository.findWithMeetingBySessionToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Participant session not found"));
        return profileRepository.findByParticipantId(participant.getId()).orElseGet(() ->
                profileRepository.save(AudienceProfile.builder().participant(participant).displayName(participant.getName()).build()));
    }

    private AudienceProfileResponse toResponse(AudienceProfile p) {
        return AudienceProfileResponse.builder().participantId(p.getParticipant().getId())
                .displayName(p.getDisplayName()).photoUrl(p.getPhotoUrl()).headline(p.getHeadline())
                .bio(p.getBio()).skills(p.getSkills()).interests(p.getInterests()).githubUrl(p.getGithubUrl())
                .linkedinUrl(p.getLinkedinUrl()).portfolioUrl(p.getPortfolioUrl()).githubVerified(p.isGithubVerified())
                .photoVisibility(p.getPhotoVisibility()).bioVisibility(p.getBioVisibility())
                .skillsVisibility(p.getSkillsVisibility()).linksVisibility(p.getLinksVisibility()).shareMode(p.getShareMode()).build();
    }
}
