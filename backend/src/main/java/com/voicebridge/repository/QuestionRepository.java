package com.voicebridge.repository;

import com.voicebridge.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {

    List<Question> findByMeetingIdOrderByCreatedAtAsc(Long meetingId);

    List<Question> findByMeetingIdOrderByPriorityScoreDescCreatedAtAsc(Long meetingId);

    long countByMeetingIdAndStatusIn(Long meetingId, List<String> statuses);
}
