package com.voicebridge.dto.request;

import com.voicebridge.entity.enums.ProfileVisibility;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateAudienceProfileRequest {
    @Size(max = 120) private String displayName;
    @Size(max = 255) private String photoUrl;
    @Size(max = 300) private String headline;
    @Size(max = 2000) private String bio;
    @Size(max = 1000) private String skills;
    @Size(max = 1000) private String interests;
    @Size(max = 500) private String githubUrl;
    @Size(max = 500) private String linkedinUrl;
    @Size(max = 500) private String portfolioUrl;
    private ProfileVisibility photoVisibility;
    private ProfileVisibility bioVisibility;
    private ProfileVisibility skillsVisibility;
    private ProfileVisibility linksVisibility;
    /** NONE, QUESTION_ONLY, or FULL. QUESTION_ONLY shares profile fields only with submitted questions. */
    private String shareMode;
}
