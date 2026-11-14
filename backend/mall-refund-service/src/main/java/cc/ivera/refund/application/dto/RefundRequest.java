package cc.ivera.refund.application.dto;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Data
public class RefundRequest {

    /**
     * 商户订单号
     */
    @NotBlank(message = "订单号不能为空")
    @Size(max = 50, message = "订单号长度不能超过50个字符")
    private String orderNo;

    /**
     * 本次退款金额（分）
     * 为空时，默认退剩余可退金额
     */
    @Positive(message = "退款金额必须大于0")
    private Integer refundAmount;

    /**
     * 退款原因
     */
    @Size(max = 50, message = "退款原因长度不能超过50个字符")
    private String reason;
}
