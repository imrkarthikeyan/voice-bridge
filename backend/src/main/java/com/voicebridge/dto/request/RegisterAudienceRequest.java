package com.voicebridge.dto.request;
import jakarta.validation.constraints.*;
import lombok.Getter; import lombok.Setter;
@Getter @Setter public class RegisterAudienceRequest {
    @NotBlank @Size(max=120) private String displayName;
    @NotBlank @Email @Size(max=180) private String email;
    @NotBlank @Size(min=8,max=100) private String password;
}
