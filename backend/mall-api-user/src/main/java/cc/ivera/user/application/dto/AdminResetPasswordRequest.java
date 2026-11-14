package cc.ivera.user.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Data
public class AdminResetPasswordRequest {
    @NotNull
    private Long userId;
    @Size(min = 8, max = 64)
    private String newPassword;
}
