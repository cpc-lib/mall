package cc.ivera.payment.application;

import cc.ivera.payment.interfaces.vo.ChannelOrderQueryVO;

/**
 * 渠道支付查单策略：向渠道查询交易状态并同步本地成功状态（不自动关单）。
 * 各支付渠道实现同一契约，由 {@link ChannelPaymentQueryDispatcher} 按支付方式分派。
 */
public interface ChannelPaymentQueryHandler {

    ChannelOrderQueryVO queryAndSyncStatus(String orderNo);
}
