package com.voicebridge.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ReviewQuestionRequest {

    @NotBlank
    @Pattern(regexp = "OPEN|SELECTED|ANSWERED|DISMISSED", message = "Unsupported question status")
    private String status;
}
