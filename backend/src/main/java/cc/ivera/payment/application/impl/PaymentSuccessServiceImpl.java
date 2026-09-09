package cc.ivera.payment.application.impl;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.PaymentOrderService;
import cc.ivera.payment.application.PaymentSuccessService;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.refund.application.ExceptionRefundService;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Date;

/**
 * 统一支付成功处理器实现（V2）。
 * 调用方（各渠道 notify / 查单同步）负责分布式锁 + 事务，本类只做状态编排。
 */
@Service
@Slf4j
public class PaymentSuccessServiceImpl implements PaymentSuccessService {

    private final OrderInfoService orderInfoService;

    private final PaymentOrderRepository paymentOrderRepository;

    private final PaymentOrderService paymentOrderService;

    private final ExceptionRefundService exceptionRefundService;

    private final DistributedLockTemplate lockTemplate;

    private final TransactionTemplate tx;

    public PaymentSuccessServiceImpl(OrderInfoService orderInfoService,
                                     PaymentOrderRepository paymentOrderRepository,
                                     PaymentOrderService paymentOrderService,
                                     ExceptionRefundService exceptionRefundService,
                                     DistributedLockTemplate lockTemplate,
                                     TransactionTemplate tx) {
        this.orderInfoService = orderInfoService;
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentOrderService = paymentOrderService;
        this.exceptionRefundService = exceptionRefundService;
        this.lockTemplate = lockTemplate;
        this.tx = tx;
    }

    @Override
    public boolean markOfflinePaid(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("标记付款缺少订单号");
        }
        return lockTemplate.execute("payment:offline:mark:" + orderNo, 5000L, -1L,
            () -> tx.execute(s ->
                handlePaymentSuccess(orderNo, PaymentConfigGateway.CHANNEL_OFFLINE, null, null)));
    }

    @Override
    public boolean handlePaymentSuccess(String orderNo, String channel, String channelOrderNo, Integer paidAmount) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("支付成功处理缺少订单号");
        }

        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("支付成功处理对应订单不存在，orderNo=" + orderNo);
        }

        // 1. 渠道感知定位本次成交的支付单：优先同渠道活跃支付单；历史订单无支付单时兼容补建。
        //    跨渠道多次发起支付后各渠道各有活跃单，按订单号盲查会把冲正锚到错误渠道支付单上。
        PaymentOrder paymentOrder = paymentOrderRepository.findActiveByOrderNoAndChannel(orderNo, channel);
        if (paymentOrder == null) {
            paymentOrder = paymentOrderRepository.findLatestSuccessByOrderNoAndChannel(orderNo, channel);
            if (paymentOrder != null) {
                // 本渠道已有成交支付单：同一渠道交易号幂等返回；不同交易号视为重复支付，
                // 兼容补建本渠道支付单并锚定本次支付冲正，不误退原成交单。
                if (channelOrderNo != null && channelOrderNo.equals(paymentOrder.getChannelOrderNo())) {
                    log.info("支付成功通知幂等命中已成交支付单，orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
                    return false;
                }
                log.info("同渠道晚到/重复支付（订单已成交），补建支付单冲正，orderNo={}, channel={}", orderNo, channel);
                paymentOrder = buildCompatiblePaymentOrder(order, channel);
                markAndRefundDuplicate(orderNo, order, paymentOrder, channelOrderNo, paidAmount);
                return false;
            }
            paymentOrder = buildCompatiblePaymentOrder(order, channel);
            log.info("订单缺少活跃支付单，兼容补建，orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
        }

        // 2. 支付单 CAS CREATED/PAYING → SUCCESS；失败说明被并发处理，幂等返回。
        Integer actualPaid = paidAmount != null ? paidAmount : order.getTotalFee();
        if (!paymentOrderService.markSuccess(paymentOrder.getPaymentNo(), channelOrderNo, actualPaid)) {
            log.info("支付单已被并发处理，幂等返回，orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
            return false;
        }

        // 3. 按订单当前状态分派。
        String legacyStatus = order.getLegacyStatus();
        if (OrderStatus.NOTPAY.getType().equals(legacyStatus)) {
            boolean updated = orderInfoService.updateStatusByOrderNoIfStatus(orderNo, OrderStatus.NOTPAY, OrderStatus.SUCCESS);
            if (!updated) {
                // 订单 CAS 失败：并发方已推进订单状态。支付单已标成功，交由并发路径或下一次通知收口。
                log.info("订单状态 CAS 失败（已被并发处理），orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
                return false;
            }
            // 首次成交收口：关闭其它渠道活跃支付单（跨渠道多次发起支付后防悬挂）。
            // 之后其它渠道晚到支付由兼容补建 + duplicatePayment 路径兜底冲正。
            int closed = paymentOrderRepository.closeActiveByOrderNoExceptPaymentNo(orderNo, paymentOrder.getPaymentNo());
            if (closed > 0) {
                log.info("订单成交收口其它渠道活跃支付单，orderNo={}, settledPaymentNo={}, closed={}",
                    orderNo, paymentOrder.getPaymentNo(), closed);
            }
            return true;
        }
        if (OrderStatus.SUCCESS.getType().equals(legacyStatus)) {
            // 订单已成交且本支付单不是原成交支付单 → 重复支付冲正。
            exceptionRefundService.duplicatePayment(paymentOrder.getPaymentNo());
            return false;
        }
        // 已关闭/已取消/超卖关闭 → 晚到支付冲正。
        exceptionRefundService.latePayment(paymentOrder.getPaymentNo());
        return false;
    }

    /**
     * 同渠道已有成交单、又一笔不同交易号支付成功：补建行 markSuccess 后按重复支付冲正（锚定本次支付）。
     */
    private void markAndRefundDuplicate(String orderNo, OrderInfo order, PaymentOrder paymentOrder,
                                        String channelOrderNo, Integer paidAmount) {
        Integer actualPaid = paidAmount != null ? paidAmount : order.getTotalFee();
        if (!paymentOrderService.markSuccess(paymentOrder.getPaymentNo(), channelOrderNo, actualPaid)) {
            log.info("补建支付单标记成功失败（并发），orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
            return;
        }
        exceptionRefundService.duplicatePayment(paymentOrder.getPaymentNo());
    }

    private PaymentOrder buildCompatiblePaymentOrder(OrderInfo order, String channel) {
        String resolvedChannel = StringUtils.hasText(channel) ? channel : order.getPaymentType();
        String channelToUse = StringUtils.hasText(resolvedChannel) ? resolvedChannel : PayType.WXPAY.getType();
        PaymentOrder paymentOrder = PaymentOrder.createCompatible(
            OrderNoUtils.getPaymentNo(), order.getOrderNo(), channelToUse,
            order.getTotalFee(), order.getExpireTime(), new Date());
        paymentOrderRepository.save(paymentOrder);
        return paymentOrder;
    }
}
