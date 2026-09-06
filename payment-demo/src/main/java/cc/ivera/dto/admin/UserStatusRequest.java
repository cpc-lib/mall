package cc.ivera.dto.admin;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class UserStatusRequest {
    @NotBlank private String userStatus; //ENABLED / DISABLED
}
