package cc.ivera.dto.refund;

import lombok.Data;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class RefundApplyRequest {
    @NotBlank private String orderNo;
    @NotBlank private String reason;
    /** 退款类型（可选）：CANCEL_BEFORE_SHIP/RETURN_AND_REFUND/REFUND_ONLY；缺省按履约状态自动归一 */
    private String refundType;
    /**
     * 退款明细（可选）：REFUND_ONLY 仅退款可传空/不传，服务端自动按整单剩余可退数量全额退；
     * RETURN_AND_REFUND 退货退款必填（服务端校验非空）。
     */
    @Valid private List<RefundApplyItemRequest> items;
}
