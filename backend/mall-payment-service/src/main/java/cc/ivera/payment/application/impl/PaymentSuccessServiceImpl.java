package cc.ivera.payment.application.impl;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.application.PaymentOrderService;
import cc.ivera.payment.application.PaymentSuccessService;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.domain.enums.PaymentOrderStatus;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentDeal;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentDealRepository;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLock;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import cc.ivera.shared.domain.mq.DuplicatePaymentMessage;
import cc.ivera.shared.domain.mq.PaymentSucceededMessage;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;

/**
 * 统一支付成功处理器实现（V2）。
 * 调用方（各渠道 notify / 查单同步）负责分布式锁 + 事务，本类只做 payment 本地成交事实编排：
 * 支付单 CAS + t_payment_deal + Outbox 事件；订单状态与冲正由下游消费者推进。
 */
@Service
@Slf4j
public class PaymentSuccessServiceImpl implements PaymentSuccessService {

    private final OrderPaymentApi orderInfoService;

    private final PaymentOrderRepository paymentOrderRepository;

    private final PaymentOrderService paymentOrderService;

    private final PaymentDealRepository paymentDealRepository;

    private final LocalMessageService localMessageService;

    private final TransactionTemplate tx;

    private final MeterRegistry meterRegistry;

    public PaymentSuccessServiceImpl(OrderPaymentApi orderInfoService,
                                     PaymentOrderRepository paymentOrderRepository,
                                     PaymentOrderService paymentOrderService,
                                     PaymentDealRepository paymentDealRepository,
                                     LocalMessageService localMessageService,
                                     TransactionTemplate tx,
                                     MeterRegistry meterRegistry) {
        this.orderInfoService = orderInfoService;
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentOrderService = paymentOrderService;
        this.paymentDealRepository = paymentDealRepository;
        this.localMessageService = localMessageService;
        this.tx = tx;
        this.meterRegistry = meterRegistry;
    }

    @Override
    @DistributedLock(key = "'payment:order:' + #orderNo", waitTimeMillis = 5000L)
    public boolean markOfflinePaid(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new BizException("标记付款缺少订单号");
        }
        OrderInfo order = orderInfoService.getOrderByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("标记付款对应订单不存在，orderNo=" + orderNo);
        }
        return tx.execute(s ->
            handlePaymentSuccess(order, PaymentConfigGateway.CHANNEL_OFFLINE, null, null));
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
        return handlePaymentSuccess(order, channel, channelOrderNo, paidAmount);
    }

    @Override
    public boolean handlePaymentSuccess(OrderInfo order, String channel, String channelOrderNo, Integer paidAmount) {
        if (order == null) {
            throw new BizException("支付成功处理对应订单不存在");
        }
        String orderNo = order.getOrderNo();

        // 0. 渠道交易号幂等：channel_order_no 只在成交时写入，(channel, channel_order_no) 唯一。
        //    若本次交易号已锚定支付单，说明成交事实已落库，直接幂等返回；
        //    否则把它再次写入另一笔活跃支付单会触发 uk_payment_channel_order 唯一约束。
        if (StringUtils.hasText(channel) && StringUtils.hasText(channelOrderNo)) {
            List<PaymentOrder> bound = paymentOrderRepository
                .listByChannelAndChannelOrderNos(channel, List.of(channelOrderNo));
            if (!bound.isEmpty()) {
                PaymentOrder settled = bound.get(0);
                log.info("渠道交易号已绑定支付单，幂等忽略，orderNo={}, paymentNo={}, channelOrderNo={}, boundOrderNo={}",
                    orderNo, settled.getPaymentNo(), channelOrderNo, settled.getOrderNo());
                return false;
            }
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
                log.info("同渠道晚到/重复支付（订单已成交），补建支付单，orderNo={}, channel={}", orderNo, channel);
                paymentOrder = buildCompatiblePaymentOrder(order, channel);
            } else {
                paymentOrder = buildCompatiblePaymentOrder(order, channel);
                log.info("订单缺少活跃支付单，兼容补建，orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
            }
        }

        // 2. 支付单 CAS CREATED/PAYING → SUCCESS；失败说明被并发处理，幂等返回。
        Integer actualPaid = paidAmount != null ? paidAmount : order.getTotalFee();
        if (!paymentOrderService.markSuccess(paymentOrder.getPaymentNo(), channelOrderNo, actualPaid)) {
            PaymentOrder reloaded = paymentOrderRepository.findByPaymentNo(paymentOrder.getPaymentNo());
            if (reloaded != null
                && PaymentOrderStatus.SUCCESS.getType().equals(reloaded.getStatus())
                && channelOrderNo != null
                && channelOrderNo.equals(reloaded.getChannelOrderNo())) {
                log.info("支付成功通知幂等命中，orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
            } else {
                log.warn("支付单状态 CAS 失败且非幂等命中，忽略本次通知，orderNo={}, paymentNo={}",
                    orderNo, paymentOrder.getPaymentNo());
            }
            return false;
        }

        // 3. 订单级成交事实：order_no 唯一键是成交唯一性的最终兜底。
        PaymentDeal deal = new PaymentDeal();
        deal.setPaymentNo(paymentOrder.getPaymentNo());
        deal.setOrderNo(orderNo);
        deal.setChannel(paymentOrder.getChannel());
        deal.setPaidAmount(actualPaid);
        deal.setPaidTime(new Date());
        if (paymentDealRepository.insert(deal)) {
            recordDeal("success");
            // 首次有效成交：关闭同订单其它渠道活跃支付单，并发出支付成交事件。
            int closed = paymentOrderRepository.closeActiveByOrderNoExceptPaymentNo(orderNo, paymentOrder.getPaymentNo());
            if (closed > 0) {
                log.info("订单成交收口其它渠道活跃支付单，orderNo={}, settledPaymentNo={}, closed={}",
                    orderNo, paymentOrder.getPaymentNo(), closed);
            }
            localMessageService.saveAndPublishAfterCommit(LocalMessage.BIZ_TYPE_PAYMENT_SUCCEEDED,
                paymentOrder.getPaymentNo(), paymentSucceededMessage(deal));
            return true;
        }

        PaymentDeal existing = paymentDealRepository.findByOrderNo(orderNo);
        if (existing != null && paymentOrder.getPaymentNo().equals(existing.getPaymentNo())) {
            log.info("成交事实已存在（同笔重复通知），orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
            return false;
        }
        // 另一笔真实支付：保留本次支付单 SUCCESS 与资金记录，交由退款侧按本次 paymentNo 原路冲正。
        log.warn("检测到同订单重复支付，发起冲正事件，orderNo={}, paymentNo={}", orderNo, paymentOrder.getPaymentNo());
        recordDeal("duplicate");
        localMessageService.saveAndPublishAfterCommit(LocalMessage.BIZ_TYPE_DUPLICATE_PAYMENT_DETECTED,
            paymentOrder.getPaymentNo(), duplicatePaymentMessage(deal));
        return true;
    }

    private void recordDeal(String result) {
        Counter.builder("mall.payment.deal").tag("result", result).register(meterRegistry).increment();
    }

    private PaymentSucceededMessage paymentSucceededMessage(PaymentDeal deal) {
        PaymentSucceededMessage message = new PaymentSucceededMessage();
        message.setEventId(deal.getPaymentNo());
        message.setOrderNo(deal.getOrderNo());
        message.setPaymentNo(deal.getPaymentNo());
        message.setChannel(deal.getChannel());
        message.setPaidAmount(deal.getPaidAmount());
        message.setPaidTime(deal.getPaidTime());
        return message;
    }

    private DuplicatePaymentMessage duplicatePaymentMessage(PaymentDeal deal) {
        DuplicatePaymentMessage message = new DuplicatePaymentMessage();
        message.setEventId(deal.getPaymentNo());
        message.setOrderNo(deal.getOrderNo());
        message.setPaymentNo(deal.getPaymentNo());
        message.setChannel(deal.getChannel());
        message.setPaidAmount(deal.getPaidAmount());
        message.setPaidTime(deal.getPaidTime());
        return message;
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
