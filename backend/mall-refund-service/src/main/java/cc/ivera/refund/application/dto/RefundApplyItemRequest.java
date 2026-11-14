package cc.ivera.refund.application.dto;

import lombok.Data;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Data
public class RefundApplyItemRequest {
    @NotNull
    private Long orderItemId;
    @NotNull
    @Min(1)
    private Integer quantity;
}
