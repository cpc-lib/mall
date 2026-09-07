package cc.ivera.dto.refund;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Data
public class RefundApplyRequest {
    @NotBlank private String orderNo;
    @NotBlank private String reason;
    /** 退款类型（可选）：CANCEL_BEFORE_SHIP/RETURN_AND_REFUND/REFUND_ONLY；缺省按履约状态自动归一 */
    private String refundType;
    @Valid @NotEmpty private List<RefundApplyItemRequest> items;
}
