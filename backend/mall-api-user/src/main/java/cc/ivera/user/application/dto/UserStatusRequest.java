package cc.ivera.user.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;

@Data
public class UserStatusRequest {
    @NotBlank
    private String userStatus; //ENABLED / DISABLED
}
