package cc.ivera.user.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class PasswordResetRequestSubmitRequest {
    @NotBlank
    private String username;
    @Size(max = 255)
    private String remark;
}
