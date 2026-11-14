package cc.ivera.payment.application.wxpay;

import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundQuery;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.application.dto.ChannelRefundStatus;
import cc.ivera.payment.application.dto.ChannelRefundSyncResult;

import java.util.List;
import java.util.Map;

public interface WxPayRefundFacade {

    ChannelRefundResult executeRefund(ChannelRefundCommand command);

    ChannelRefundStatus queryRefund(ChannelRefundQuery query);

    ChannelRefundSyncResult queryRefundStatusForSync(ChannelRefundQuery query);

    List<ChannelRefundSyncResult> queryOrderRefundsForSync(String orderNo, String channel);

    ChannelRefundSyncResult processRefundNotify(Map<String, Object> bodyMap);
}
