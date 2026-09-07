package cc.ivera.dto.admin;

import lombok.Data;
import javax.validation.constraints.NotBlank;

@Data
public class ProductStatusRequest {
    @NotBlank private String productStatus; //ENABLED / DISABLED
}
