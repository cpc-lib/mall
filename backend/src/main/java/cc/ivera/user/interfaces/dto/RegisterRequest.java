package cc.ivera.user.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class RegisterRequest {
    @NotBlank
    @Size(min = 3, max = 32)
    private String username;
    @NotBlank
    @Size(min = 8, max = 64)
    private String password;
}
