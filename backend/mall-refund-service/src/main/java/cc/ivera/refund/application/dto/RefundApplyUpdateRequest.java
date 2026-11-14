package cc.ivera.refund.application.dto;

import lombok.Data;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class RefundApplyUpdateRequest {
    @NotBlank
    private String reason;
    @Valid
    @NotEmpty
    private List<RefundApplyItemRequest> items;
}
