package cc.ivera.dto.refund;

import lombok.Data;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class PriceAdjustmentRequest {
    @NotBlank private String orderNo;
    /** 差价退款金额(分)，受订单剩余可退额度约束 */
    @NotNull @Min(1) private Integer amount;
    private String reason;
}
