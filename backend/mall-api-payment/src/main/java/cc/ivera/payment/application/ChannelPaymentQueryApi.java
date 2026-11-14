package cc.ivera.payment.application;

import cc.ivera.payment.application.dto.ChannelOrderQueryVO;

/**
 * 渠道查单契约：按支付方式分发渠道查单并同步支付状态。
 */
public interface ChannelPaymentQueryApi {

    ChannelOrderQueryVO query(String payType, String orderNo);
}
