package com.voicebridge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voicebridge.entity.Presentation;
import com.voicebridge.entity.Slide;
import com.voicebridge.repository.SlideRepository;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class SlideAnalysisServiceTest {

    @Test
    void extractsTitleStructureAndCodeMetadataFromPptx() throws Exception {
        XMLSlideShow show = new XMLSlideShow();
        var slide = show.createSlide();
        var textBox = slide.createTextBox();
        textBox.setText("Microservices Architecture\n    public class Gateway {}");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        show.write(output);
        show.close();

        SlideRepository repository = mock(SlideRepository.class);
        when(repository.findByPresentationIdOrderBySlideNumberAsc(7L)).thenReturn(List.of());
        Presentation presentation = Presentation.builder().id(7L).build();

        new SlideAnalysisService(repository, new ObjectMapper()).analyze(
                presentation, new ByteArrayInputStream(output.toByteArray()), "pptx");

        ArgumentCaptor<Slide> captor = ArgumentCaptor.forClass(Slide.class);
        verify(repository).save(captor.capture());
        Slide saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("Microservices Architecture");
        assertThat(saved.isHasCode()).isTrue();
        assertThat(saved.getStructureJson()).contains("\"blocks\":");
        assertThat(saved.getAnalysisStatus()).isEqualTo("QUEUED");
    }
}
