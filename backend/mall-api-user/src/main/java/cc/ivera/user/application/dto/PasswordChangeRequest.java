package cc.ivera.user.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class PasswordChangeRequest {
    @NotBlank
    private String oldPassword;
    @NotBlank
    @Size(min = 8, max = 64)
    private String newPassword;
}
