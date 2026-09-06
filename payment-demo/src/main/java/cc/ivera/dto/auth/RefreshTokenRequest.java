package cc.ivera.dto.auth;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class RefreshTokenRequest {
    @NotBlank private String refreshToken;
}
