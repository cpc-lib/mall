package cc.ivera.payment.application.dto;

import lombok.Data;

/**
 * 支付服务内部接口：渠道查单 / 渠道关单核对参数。
 */
@Data
public class InternalChannelRequest {

    private String payType;

    private String orderNo;
}
