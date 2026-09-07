package cc.ivera.dto.refund;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Data
public class RefundApplyItemRequest {
    @NotNull private Long orderItemId;
    @NotNull @Min(1) private Integer quantity;
}
