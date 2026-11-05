package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "questions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meeting_id", nullable = false)
    private Meeting meeting;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private Participant participant;
    @Column(nullable = false, length = 6000)
    private String text;
    @Column(name = "original_text", length = 6000)
    private String originalText;
    @Column(nullable = false, length = 16)
    private String language;
    @Column(name = "translated_text", length = 6000)
    private String translatedText;
    @Column(nullable = false, length = 20)
    private String source;
    @Column(nullable = false, length = 32)
    private String type;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(name = "slide_number")
    private Integer slideNumber;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "duplicate_of_id")
    private Question duplicateOf;
    @Column(name = "priority_score", nullable = false)
    private Integer priorityScore;
    @Column(name = "priority_reason", length = 500)
    private String priorityReason;
    @Column(name = "start_ms")
    private Long startMs;
    @Column(name = "end_ms")
    private Long endMs;
    @Column(name = "question_confidence")
    private Double questionConfidence;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
