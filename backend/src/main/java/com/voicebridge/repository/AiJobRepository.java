package com.voicebridge.repository;

import com.voicebridge.entity.AiJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import java.util.List;

public interface AiJobRepository extends JpaRepository<AiJob, Long> {

    List<AiJob> findByStatusAndNextAttemptAtLessThanEqual(String status, Instant now);
}
