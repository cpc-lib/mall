package cc.ivera.payment.application;

import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.application.wxpay.WxPayOrderFacade;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 按支付方式分发渠道查单/关单核对：延迟关单消费者与超时关单调度器共用同一套分派规则。
 */
@Component
@Slf4j
public class ChannelOrderStatusDispatcher {

    private final Map<String, Consumer<String>> handlers;

    public ChannelOrderStatusDispatcher(WxPayOrderFacade wxPayOrderFacade, AliPayService aliPayService) {
        Map<String, Consumer<String>> map = new HashMap<>();
        map.put(PayType.WXPAY.getType(), wxPayOrderFacade::checkOrderStatus);
        map.put(PayType.ALIPAY.getType(), aliPayService::checkOrderStatus);
        this.handlers = Collections.unmodifiableMap(map);
    }

    public void checkOrderStatus(String payType, String orderNo) {
        Consumer<String> handler = handlers.get(payType);
        if (handler == null) {
            log.warn("未知支付类型，无法处理渠道查单，orderNo={}, paymentType={}", orderNo, payType);
            return;
        }
        handler.accept(orderNo);
    }
}
