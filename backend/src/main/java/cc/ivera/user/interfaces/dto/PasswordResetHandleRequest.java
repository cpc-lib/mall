package cc.ivera.user.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class PasswordResetHandleRequest {
    @Size(max = 255)
    private String adminRemark;
}
