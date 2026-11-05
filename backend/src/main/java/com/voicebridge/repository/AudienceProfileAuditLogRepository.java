package com.voicebridge.repository;
import com.voicebridge.entity.AudienceProfileAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
public interface AudienceProfileAuditLogRepository extends JpaRepository<AudienceProfileAuditLog, Long> {
    long deleteByCreatedAtBefore(Instant cutoff);
}
