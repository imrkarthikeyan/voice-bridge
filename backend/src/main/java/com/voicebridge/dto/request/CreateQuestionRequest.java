package com.voicebridge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateQuestionRequest {

    @NotBlank
    @Size(max = 6000)
    private String text;
    private String originalText;
    private String language;
}
