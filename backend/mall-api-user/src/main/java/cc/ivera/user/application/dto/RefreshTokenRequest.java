package cc.ivera.user.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class RefreshTokenRequest {
    @NotBlank
    private String refreshToken;
}
