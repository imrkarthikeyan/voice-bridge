package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity
@Table(name = "audience_accounts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class AudienceAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 180) private String email;
    @Column(name = "password_hash", nullable = false) private String passwordHash;
    @Column(nullable = false, length = 120) private String displayName;
    @Column(name = "github_subject") private String githubSubject;
    @Column(nullable = false, updatable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
    @PrePersist void create() { createdAt = updatedAt = Instant.now(); normalize(); }
    @PreUpdate void update() { updatedAt = Instant.now(); normalize(); }
    private void normalize() { if (email != null) email = email.trim().toLowerCase(); }
}
