package com.voicebridge.service;

import com.voicebridge.entity.Meeting;
import com.voicebridge.entity.SlideVisit;
import com.voicebridge.repository.SlideVisitRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SlideAlignmentServiceTest {

    @Test
    void selectsPreviousSlideDuringGraceWindowAfterBoundary() {
        SlideVisitRepository repository = mock(SlideVisitRepository.class);
        Meeting meeting = Meeting.builder().id(11L).startedAt(Instant.parse("2026-01-01T00:00:00Z")).build();
        when(repository.findByMeetingIdOrderByEnteredMsAsc(11L)).thenReturn(List.of(
                SlideVisit.builder().slideNumber(8).enteredMs(1000L).leftMs(5000L).build(),
                SlideVisit.builder().slideNumber(9).enteredMs(5000L).leftMs(null).build()));

        SlideAlignmentService service = new SlideAlignmentService(repository);
        assertThat(service.slideAt(meeting, 5100L)).isEqualTo(8);
        assertThat(service.slideAt(meeting, 7000L)).isEqualTo(9);
    }
}
