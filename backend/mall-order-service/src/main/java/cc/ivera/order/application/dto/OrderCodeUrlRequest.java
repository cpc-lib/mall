package cc.ivera.order.application.dto;

import lombok.Data;

/**
 * 内部契约请求：payment-service 回写支付二维码地址。
 */
@Data
public class OrderCodeUrlRequest {

    private String codeUrl;
}
