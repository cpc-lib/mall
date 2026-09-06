package cc.ivera.service.impl;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.PaymentOrder;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PaymentOrderStatus;
import cc.ivera.enums.PayType;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.PaymentOrderMapper;
import cc.ivera.service.ExceptionRefundService;
import cc.ivera.service.OrderInfoService;
import cc.ivera.service.PaymentOrderService;
import cc.ivera.service.PaymentSuccessService;
import cc.ivera.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 统一支付成功处理器实现（V2）。
 * 调用方（各渠道 notify / 查单同步）负责分布式锁 + 事务，本类只做状态编排。
 */
@Service
@Slf4j
public class PaymentSuccessServiceImpl implements PaymentSuccessService {

    private final OrderInfoService orderInfoService;

    private final PaymentOrderMapper paymentOrderMapper;

    private final PaymentOrderService paymentOrderService;

    private final ExceptionRefundService exceptionRefundService;

    public PaymentSuccessServiceImpl(OrderInfoService orderInfoService,
                                     PaymentOrderMapper paymentOrderMapper,
                                     PaymentOrderService paymentOrderService,
                                     ExceptionRefundService exceptionRefundService) {
        this.orderInfoService = orderInfoService;
        this.paymentOrderMapper = paymentOrderMapper;
        this.paymentOrderService = paymentOrderService;
        this.exceptionRefundService = exceptionRefundService;
    }

    @Override
    public boolean handlePaymentSuccess(String orderNo, String channel, String channelOrderNo, Integer paidAmount) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("支付成功处理缺少订单号");
        }

        OrderInfo order = orderInfoService.getOrderByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException("支付成功处理对应订单不存在，orderNo=" + orderNo);
        }

        // 1. 渠道感知定位本次成交的支付单：优先同渠道活跃支付单；历史订单无支付单时兼容补建。
        //    跨渠道多次发起支付后各渠道各有活跃单，按订单号盲查会把冲正锚到错误渠道支付单上。
        PaymentOrder paymentOrder = findActiveByOrderNoAndChannel(orderNo, channel);
        if (paymentOrder == null) {
            paymentOrder = findLatestSuccessByOrderNoAndChannel(orderNo, channel);
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
            int closed = paymentOrderMapper.closeActiveByOrderNoExceptPaymentNo(orderNo, paymentOrder.getPaymentNo());
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

    /** 同渠道已有成交单、又一笔不同交易号支付成功：补建行 markSuccess 后按重复支付冲正（锚定本次支付）。 */
    private void markAndRefundDuplicate(String orderNo, OrderInfo order, PaymentOrder paymentOrder,
                                        String channelOrderNo, Integer paidAmount) {
        Integer actualPaid = paidAmount != null ? paidAmount : order.getTotalFee();
        if (!paymentOrderService.markSuccess(paymentOrder.getPaymentNo(), channelOrderNo, actualPaid)) {
            log.info("补建支付单标记成功失败（并发），orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
            return;
        }
        exceptionRefundService.duplicatePayment(paymentOrder.getPaymentNo());
    }

    private PaymentOrder findActiveByOrderNoAndChannel(String orderNo, String channel) {
        List<PaymentOrder> list = paymentOrderMapper.selectList(new QueryWrapper<PaymentOrder>()
                .eq("order_no", orderNo)
                .eq("channel", channel)
                .in("status", PaymentOrderStatus.CREATED.getType(), PaymentOrderStatus.PAYING.getType())
                .orderByDesc("id")
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private PaymentOrder findLatestSuccessByOrderNoAndChannel(String orderNo, String channel) {
        List<PaymentOrder> list = paymentOrderMapper.selectList(new QueryWrapper<PaymentOrder>()
                .eq("order_no", orderNo)
                .eq("channel", channel)
                .eq("status", PaymentOrderStatus.SUCCESS.getType())
                .orderByDesc("id")
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private PaymentOrder buildCompatiblePaymentOrder(OrderInfo order, String channel) {
        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo(OrderNoUtils.getPaymentNo());
        paymentOrder.setOrderNo(order.getOrderNo());
        String resolvedChannel = StringUtils.hasText(channel) ? channel : order.getPaymentType();
        paymentOrder.setChannel(StringUtils.hasText(resolvedChannel) ? resolvedChannel : PayType.WXPAY.getType());
        paymentOrder.setRequestAmount(order.getTotalFee());
        paymentOrder.setRefundFrozenAmount(0);
        paymentOrder.setRefundedAmount(0);
        paymentOrder.setStatus(PaymentOrderStatus.PAYING.getType());
        paymentOrder.setExpireTime(order.getExpireTime() != null ? order.getExpireTime() : new Date());
        paymentOrderMapper.insert(paymentOrder);
        return paymentOrder;
    }
}
