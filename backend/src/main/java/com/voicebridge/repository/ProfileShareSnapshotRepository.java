package com.voicebridge.repository;
import com.voicebridge.entity.ProfileShareSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface ProfileShareSnapshotRepository extends JpaRepository<ProfileShareSnapshot, Long> {
    List<ProfileShareSnapshot> findByMeetingIdAndParticipantIdOrderByCreatedAtDesc(Long meetingId, Long participantId);
}
