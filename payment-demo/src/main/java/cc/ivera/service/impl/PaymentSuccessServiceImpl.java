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

        // 1. 定位本次成交的支付单：优先活跃支付单；历史订单无支付单时兼容补建。
        PaymentOrder paymentOrder = findActiveByOrderNo(orderNo);
        if (paymentOrder == null) {
            paymentOrder = findLatestSuccessByOrderNo(orderNo);
            if (paymentOrder != null) {
                // 已有成交支付单：同一渠道交易号幂等返回；不同交易号视为重复支付。
                if (channelOrderNo != null && channelOrderNo.equals(paymentOrder.getChannelOrderNo())) {
                    log.info("支付成功通知幂等命中已成交支付单，orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
                    return false;
                }
                exceptionRefundService.duplicatePayment(paymentOrder.getPaymentNo());
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

    private PaymentOrder findActiveByOrderNo(String orderNo) {
        List<PaymentOrder> list = paymentOrderMapper.selectList(new QueryWrapper<PaymentOrder>()
                .eq("order_no", orderNo)
                .in("status", PaymentOrderStatus.CREATED.getType(), PaymentOrderStatus.PAYING.getType())
                .orderByDesc("id")
                .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private PaymentOrder findLatestSuccessByOrderNo(String orderNo) {
        List<PaymentOrder> list = paymentOrderMapper.selectList(new QueryWrapper<PaymentOrder>()
                .eq("order_no", orderNo)
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
