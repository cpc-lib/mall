package cc.ivera.dto.refund;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class RefundApplyUpdateRequest {
    @NotBlank private String reason;
    @Valid @NotEmpty private List<RefundApplyItemRequest> items;
}
