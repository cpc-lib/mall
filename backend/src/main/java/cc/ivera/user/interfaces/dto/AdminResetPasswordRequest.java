package cc.ivera.user.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Data
public class AdminResetPasswordRequest {
    @NotNull
    private Long userId;
    @Size(min = 8, max = 64)
    private String newPassword;
}
