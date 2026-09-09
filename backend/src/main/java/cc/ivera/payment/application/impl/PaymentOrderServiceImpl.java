package cc.ivera.payment.application.impl;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.PaymentOrderService;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 支付单生命周期服务实现（V2）。
 */
@Service
@Slf4j
public class PaymentOrderServiceImpl implements PaymentOrderService {

    private final PaymentOrderRepository paymentOrderRepository;

    public PaymentOrderServiceImpl(PaymentOrderRepository paymentOrderRepository) {
        this.paymentOrderRepository = paymentOrderRepository;
    }

    @Override
    public PaymentOrder startPayment(OrderInfo order, String channel) {
        if (order == null || !StringUtils.hasText(order.getOrderNo())) {
            throw new BizException("发起支付缺少有效订单");
        }
        if (!StringUtils.hasText(channel)) {
            throw new BizException("发起支付缺少渠道编码");
        }

        // 渠道感知：同渠道复用活跃支付单；跨渠道各建一行（旧渠道活跃单不提前关闭，
        // 用户扫旧渠道二维码支付仍能按渠道正确落行，订单终态统一收口其它渠道活跃单）。
        PaymentOrder active = paymentOrderRepository.findActiveByOrderNoAndChannel(order.getOrderNo(), channel);
        if (active != null) {
            log.info("订单存在同渠道活跃支付单，复用，orderNo={}, channel={}, paymentNo={}",
                    order.getOrderNo(), channel, active.getPaymentNo());
            return active;
        }

        Date now = new Date();
        PaymentOrder paymentOrder = PaymentOrder.startNew(
                OrderNoUtils.getPaymentNo(), order.getOrderNo(), channel,
                order.getTotalFee(), order.getExpireTime(), now);
        paymentOrderRepository.save(paymentOrder);
        log.info("创建支付单，orderNo={}, paymentNo={}, channel={}, expireTime={}",
                order.getOrderNo(), paymentOrder.getPaymentNo(), channel, paymentOrder.getExpireTime());
        return paymentOrder;
    }

    @Override
    public void markPaying(String paymentNo, String codeUrl) {
        if (!StringUtils.hasText(paymentNo)) {
            return;
        }
        paymentOrderRepository.markPaying(paymentNo, codeUrl);
    }

    @Override
    public boolean markSuccess(String paymentNo, String channelOrderNo, Integer paidAmount) {
        if (!StringUtils.hasText(paymentNo)) {
            return false;
        }
        return paymentOrderRepository.markSuccess(paymentNo, channelOrderNo, paidAmount);
    }
}
