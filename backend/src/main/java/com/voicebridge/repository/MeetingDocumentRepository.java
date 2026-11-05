package com.voicebridge.repository;

import com.voicebridge.entity.MeetingDocument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MeetingDocumentRepository extends JpaRepository<MeetingDocument, Long> {
    Optional<MeetingDocument> findByMeetingId(Long meetingId);
}
