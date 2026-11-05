package com.voicebridge.repository;
import com.voicebridge.entity.ParticipantAccountLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface ParticipantAccountLinkRepository extends JpaRepository<ParticipantAccountLink, Long> {
    Optional<ParticipantAccountLink> findByAccountIdAndParticipantId(Long accountId, Long participantId);
    boolean existsByParticipantId(Long participantId);
}
