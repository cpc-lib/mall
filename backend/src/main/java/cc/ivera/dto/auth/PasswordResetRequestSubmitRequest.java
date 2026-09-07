package cc.ivera.dto.auth;

import lombok.Data;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

@Data
public class PasswordResetRequestSubmitRequest {
    @NotBlank private String username;
    @Size(max = 255) private String remark;
}
