package cc.ivera.user.interfaces.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserStatusRequest {
    @NotBlank
    private String userStatus; //ENABLED / DISABLED
}
