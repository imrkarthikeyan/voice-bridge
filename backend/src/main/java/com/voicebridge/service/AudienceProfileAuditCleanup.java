package com.voicebridge.service;
import com.voicebridge.repository.AudienceProfileAuditLogRepository;
import lombok.RequiredArgsConstructor; import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
import java.time.Instant; import java.time.temporal.ChronoUnit;
@Component @RequiredArgsConstructor
public class AudienceProfileAuditCleanup {
 private final AudienceProfileAuditLogRepository repository;
 @Scheduled(cron="${app.audience.audit-cleanup-cron:0 15 3 * * *}")
 public void removeExpiredLogs(){ repository.deleteByCreatedAtBefore(Instant.now().minus(90, ChronoUnit.DAYS)); }
}
