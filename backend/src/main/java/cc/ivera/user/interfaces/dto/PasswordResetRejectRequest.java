package cc.ivera.user.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.Size;

@Data
public class PasswordResetRejectRequest {
    @Size(max = 255)
    private String adminRemark;
}
