package com.voicebridge.entity;

import com.voicebridge.entity.enums.ProfileVisibility;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "audience_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AudienceProfile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false, unique = true)
    private Participant participant;

    @Column(length = 120)
    private String displayName;
    @Column(length = 255)
    private String photoUrl;
    @Column(length = 300)
    private String headline;
    @Column(length = 2000)
    private String bio;
    @Column(length = 1000)
    private String skills;
    @Column(length = 1000)
    private String interests;
    @Column(length = 500)
    private String githubUrl;
    @Column(length = 500)
    private String linkedinUrl;
    @Column(length = 500)
    private String portfolioUrl;
    @Column(nullable = false)
    private boolean githubVerified;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileVisibility photoVisibility;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileVisibility bioVisibility;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileVisibility skillsVisibility;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProfileVisibility linksVisibility;
    @Column(name = "share_mode", nullable = false, length = 20)
    private String shareMode;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    void createTimestamps() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (photoVisibility == null) photoVisibility = ProfileVisibility.NONE;
        if (bioVisibility == null) bioVisibility = ProfileVisibility.NONE;
        if (skillsVisibility == null) skillsVisibility = ProfileVisibility.NONE;
        if (linksVisibility == null) linksVisibility = ProfileVisibility.NONE;
        if (shareMode == null) shareMode = "NONE";
    }

    @PreUpdate
    void updateTimestamp() {
        updatedAt = Instant.now();
    }
}
