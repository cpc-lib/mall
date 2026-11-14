package cc.ivera.payment.application.dto;

import lombok.Data;

/**
 * 支付服务内部接口：统一支付成功受理参数。
 */
@Data
public class InternalPaymentSuccessRequest {

    private String orderNo;

    private String channel;

    private String channelOrderNo;

    private Integer paidAmount;
}
