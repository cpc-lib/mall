package cc.ivera.user.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 32)
    private String username;
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
