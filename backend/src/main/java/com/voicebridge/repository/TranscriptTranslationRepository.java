package com.voicebridge.repository;

import com.voicebridge.entity.TranscriptTranslation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TranscriptTranslationRepository extends JpaRepository<TranscriptTranslation, Long> {

    Optional<TranscriptTranslation> findBySegmentIdAndTargetLanguage(Long segmentId, String targetLanguage);

    java.util.List<TranscriptTranslation> findBySegmentId(Long segmentId);
}
