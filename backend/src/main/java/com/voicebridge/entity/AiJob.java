package com.voicebridge.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "ai_jobs")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meeting_id")
    private Meeting meeting;
    @Column(name = "job_type", nullable = false, length = 60)
    private String jobType;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(columnDefinition = "TEXT")
    private String payload;
    @Column(columnDefinition = "TEXT")
    private String result;
    @Column(name = "error_message", length = 2000)
    private String errorMessage;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;
    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;
    @Column(name = "max_attempts", nullable = false)
    private int maxAttempts;
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
