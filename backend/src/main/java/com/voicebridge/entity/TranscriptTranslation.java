package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "transcript_translations", uniqueConstraints = {
    @UniqueConstraint(name = "uk_translation_segment_language",
            columnNames = {"segment_id", "target_language"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TranscriptTranslation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "segment_id", nullable = false)
    private TranscriptSegment segment;

    @Column(name = "target_language", nullable = false, length = 16)
    private String targetLanguage;

    @Column(nullable = false, length = 4000)
    private String text;

    @Column(length = 80)
    private String model;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
    }
}
