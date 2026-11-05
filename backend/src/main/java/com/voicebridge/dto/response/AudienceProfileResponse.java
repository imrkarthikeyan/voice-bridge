package com.voicebridge.dto.response;

import com.voicebridge.entity.enums.ProfileVisibility;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudienceProfileResponse {
    private Long participantId;
    private String displayName;
    private String photoUrl;
    private String headline;
    private String bio;
    private String skills;
    private String interests;
    private String githubUrl;
    private String linkedinUrl;
    private String portfolioUrl;
    private boolean githubVerified;
    private ProfileVisibility photoVisibility;
    private ProfileVisibility bioVisibility;
    private ProfileVisibility skillsVisibility;
    private ProfileVisibility linksVisibility;
    private String shareMode;
}
