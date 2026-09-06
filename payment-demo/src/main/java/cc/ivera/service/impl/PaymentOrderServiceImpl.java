package cc.ivera.service.impl;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.PaymentOrder;
import cc.ivera.enums.PaymentOrderStatus;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.PaymentOrderMapper;
import cc.ivera.service.PaymentOrderService;
import cc.ivera.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 支付单生命周期服务实现（V2）。
 */
@Service
@Slf4j
public class PaymentOrderServiceImpl implements PaymentOrderService {

    /** 距订单过期不足 60 秒时拒绝发起新支付。 */
    private static final long MIN_PAY_WINDOW_MS = 60_000L;

    /** 支付单最长有效期：min(order.expire_time, now + 2h)。 */
    private static final long MAX_PAYMENT_LIVE_MS = 2L * 60L * 60L * 1000L;

    private final PaymentOrderMapper paymentOrderMapper;

    public PaymentOrderServiceImpl(PaymentOrderMapper paymentOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
    }

    @Override
    public PaymentOrder startPayment(OrderInfo order, String channel) {
        if (order == null || !StringUtils.hasText(order.getOrderNo())) {
            throw new BizException("发起支付缺少有效订单");
        }
        if (!StringUtils.hasText(channel)) {
            throw new BizException("发起支付缺少渠道编码");
        }

        PaymentOrder active = findActiveByOrderNo(order.getOrderNo());
        if (active != null) {
            log.info("订单存在活跃支付单，复用，orderNo={}, paymentNo={}", order.getOrderNo(), active.getPaymentNo());
            return active;
        }

        Date now = new Date();
        if (order.getExpireTime() != null && order.getExpireTime().getTime() - now.getTime() < MIN_PAY_WINDOW_MS) {
            throw new BizException("订单即将过期，请重新下单，orderNo=" + order.getOrderNo());
        }

        PaymentOrder paymentOrder = new PaymentOrder();
        paymentOrder.setPaymentNo(OrderNoUtils.getPaymentNo());
        paymentOrder.setOrderNo(order.getOrderNo());
        paymentOrder.setChannel(channel);
        paymentOrder.setRequestAmount(order.getTotalFee());
        paymentOrder.setRefundFrozenAmount(0);
        paymentOrder.setRefundedAmount(0);
        paymentOrder.setStatus(PaymentOrderStatus.CREATED.getType());
        paymentOrder.setExpireTime(resolveExpireTime(order.getExpireTime(), now));
        paymentOrderMapper.insert(paymentOrder);
        log.info("创建支付单，orderNo={}, paymentNo={}, channel={}, expireTime={}",
                order.getOrderNo(), paymentOrder.getPaymentNo(), channel, paymentOrder.getExpireTime());
        return paymentOrder;
    }

    @Override
    public void markPaying(String paymentNo, String codeUrl) {
        if (!StringUtils.hasText(paymentNo)) {
            return;
        }
        PaymentOrder update = new PaymentOrder();
        update.setStatus(PaymentOrderStatus.PAYING.getType());
        update.setCodeUrl(codeUrl);
        paymentOrderMapper.update(update, new UpdateWrapper<PaymentOrder>()
                .eq("payment_no", paymentNo)
                .eq("status", PaymentOrderStatus.CREATED.getType()));
    }

    @Override
    public boolean markSuccess(String paymentNo, String channelOrderNo, Integer paidAmount) {
        if (!StringUtils.hasText(paymentNo)) {
            return false;
        }
        PaymentOrder update = new PaymentOrder();
        update.setStatus(PaymentOrderStatus.SUCCESS.getType());
        update.setChannelOrderNo(channelOrderNo);
        update.setPaidAmount(paidAmount);
        update.setPaidTime(new Date());
        int updated = paymentOrderMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<PaymentOrder>()
                        .eq("payment_no", paymentNo)
                        .in("status", PaymentOrderStatus.CREATED.getType(), PaymentOrderStatus.PAYING.getType()));
        return updated > 0;
    }

    private PaymentOrder findActiveByOrderNo(String orderNo) {
        List<PaymentOrder> list = paymentOrderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PaymentOrder>()
                        .eq("order_no", orderNo)
                        .in("status", PaymentOrderStatus.CREATED.getType(), PaymentOrderStatus.PAYING.getType())
                        .orderByDesc("id")
                        .last("limit 1"));
        return list.isEmpty() ? null : list.get(0);
    }

    private Date resolveExpireTime(Date orderExpireTime, Date now) {
        Date maxLive = new Date(now.getTime() + MAX_PAYMENT_LIVE_MS);
        if (orderExpireTime == null || orderExpireTime.after(maxLive)) {
            return maxLive;
        }
        return orderExpireTime;
    }
}
