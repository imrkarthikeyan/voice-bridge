package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "slides", uniqueConstraints = {
    @UniqueConstraint(name = "uk_slides_presentation_number", columnNames = {"presentation_id", "slide_number"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Slide {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "presentation_id", nullable = false)
    private Presentation presentation;
    @Column(name = "slide_number", nullable = false)
    private Integer slideNumber;
    @Column(length = 500)
    private String title;
    @Column(name = "extracted_text", nullable = false, length = 20000)
    private String extractedText;
    @Column(name = "concepts_json", nullable = false, length = 10000)
    private String conceptsJson;
    @Column(columnDefinition = "TEXT")
    private String notes;
    @Column(name = "structure_json", nullable = false, columnDefinition = "TEXT")
    private String structureJson;
    @Column(columnDefinition = "TEXT")
    private String embedding;
    @Column(name = "analysis_status", nullable = false, length = 20)
    private String analysisStatus;
    @Column(name = "analysis_error", length = 2000)
    private String analysisError;
    @Column(name = "language", length = 16)
    private String language;
    @Column(name = "has_code", nullable = false)
    private boolean hasCode;
    @Column(name = "has_table", nullable = false)
    private boolean hasTable;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
        if (structureJson == null) {
            structureJson = "{}";
        }
        if (analysisStatus == null) {
            analysisStatus = "READY";
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
