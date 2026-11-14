package cc.ivera.user.application.dto;

import lombok.Data;

import jakarta.validation.constraints.Size;

@Data
public class PasswordResetRejectRequest {
    @Size(max = 255)
    private String adminRemark;
}
