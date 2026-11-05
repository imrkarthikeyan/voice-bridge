package com.voicebridge.repository;

import com.voicebridge.entity.AudienceProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AudienceProfileRepository extends JpaRepository<AudienceProfile, Long> {
    void deleteByParticipantId(Long participantId);
    Optional<AudienceProfile> findByParticipantId(Long participantId);
}
