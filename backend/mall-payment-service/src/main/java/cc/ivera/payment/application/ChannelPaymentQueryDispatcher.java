package cc.ivera.payment.application;

import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.application.dto.ChannelOrderQueryVO;
import cc.ivera.shared.domain.exception.BizException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 按支付方式分发渠道查单：用户端支付结果轮询与管理端渠道查询共用同一套分派规则。
 */
@Component
public class ChannelPaymentQueryDispatcher implements ChannelPaymentQueryApi {

    private final Map<String, ChannelPaymentQueryHandler> handlers;

    public ChannelPaymentQueryDispatcher(@Qualifier("wxChannelPaymentQueryHandler") ChannelPaymentQueryHandler wxHandler, AliPayService aliPayService) {
        Map<String, ChannelPaymentQueryHandler> map = new HashMap<>();
        map.put(PayType.WXPAY.getType(), wxHandler);
        map.put(PayType.ALIPAY.getType(), aliPayService);
        this.handlers = Collections.unmodifiableMap(map);
    }

    @Override
    public ChannelOrderQueryVO query(String payType, String orderNo) {
        ChannelPaymentQueryHandler handler = handlers.get(payType);
        if (handler == null) {
            throw new BizException("该订单无支付渠道，无法查询支付结果");
        }
        return handler.queryAndSyncStatus(orderNo);
    }
}
