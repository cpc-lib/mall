package cc.ivera.api.payment;

import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;

import java.util.List;
import java.util.Map;

/**
 * 支付渠道退款契约：退款上下文只通过本接口触达渠道，不直接依赖支付渠道实现或退款表。
 * P4a 为进程内实现，P4b 可由远程适配器替换，契约不变。
 */
public interface PaymentChannelRefundApi {

    ChannelRefundResult executeRefund(ChannelRefundCommand command);

    ChannelRefundStatus queryRefund(ChannelRefundQuery query);

    ChannelRefundSyncResult queryRefundStatusForSync(ChannelRefundQuery query);

    List<ChannelRefundSyncResult> queryOrderRefundsForSync(String orderNo, String channel);

    ChannelRefundSyncResult processRefundNotify(Map<String, Object> bodyMap);
}
