package cc.ivera.order.application.dto;

import lombok.Data;

/**
 * 内部契约请求：payment-service 按支付渠道建单或复用未支付订单。
 */
@Data
public class OrderCreateOrReuseRequest {

    private Long productId;

    private String paymentType;

    private Long paymentAppId;

    private String paymentChannelCode;
}
