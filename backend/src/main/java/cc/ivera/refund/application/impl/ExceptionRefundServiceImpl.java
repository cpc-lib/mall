package cc.ivera.refund.application.impl;

import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.payment.application.AliPayService;
import cc.ivera.payment.application.wxpay.WxPayRefundFacade;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.refund.application.ExceptionRefundService;
import cc.ivera.refund.domain.enums.RefundApprovalStatus;
import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.enums.RefundType;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.model.RefundItem;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import cc.ivera.refund.domain.repository.RefundItemRepository;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

/**
 * 异常支付自动冲正服务（V2）：
 * - 超卖熔断（trigger）：整单退款 + 订单置 OVER_SOLD_CLOSED；
 * - 重复支付（duplicatePayment）/ 晚到支付（latePayment）：
 * 系统免审创建 RefundOrder（refund_type=DUPLICATE_PAYMENT/LATE_PAYMENT），
 * 不冻结订单售后额度、不写 refund_item、不动订单 refund_status，
 * 仅在 PaymentOrder 渠道资金防线内（freeze→refund→settle）完成原路退款。
 */
@Service
@Slf4j
public class ExceptionRefundServiceImpl implements ExceptionRefundService {

    private final DistributedLockTemplate lock;
    private final TransactionTemplate tx;
    private final OrderRepository orderRepository;
    private final RefundOrderRepository refundOrderRepository;
    private final RefundItemRepository refundItemRepository;
    private final RefundInfoRepository refundInfoRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final AliPayService aliPayService;
    private final WxPayRefundFacade wxPayRefundFacade;

    public ExceptionRefundServiceImpl(DistributedLockTemplate lock,
                                      TransactionTemplate tx,
                                      OrderRepository orderRepository,
                                      RefundOrderRepository refundOrderRepository,
                                      RefundItemRepository refundItemRepository,
                                      RefundInfoRepository refundInfoRepository,
                                      PaymentOrderRepository paymentOrderRepository,
                                      // @Lazy 打断 aliPayServiceImpl → paymentSuccessServiceImpl → 本类 → aliPayServiceImpl 构造器循环
                                      @Lazy AliPayService aliPayService,
                                      WxPayRefundFacade wxPayRefundFacade) {
        this.lock = lock;
        this.tx = tx;
        this.orderRepository = orderRepository;
        this.refundOrderRepository = refundOrderRepository;
        this.refundItemRepository = refundItemRepository;
        this.refundInfoRepository = refundInfoRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.aliPayService = aliPayService;
        this.wxPayRefundFacade = wxPayRefundFacade;
    }

    @Override
    public void trigger(String orderNo, String reason) {
        lock.execute("oversold:refund:" + orderNo, 5000L, -1L, () -> {
            RefundInfo refund = tx.execute(s -> prepareOversold(orderNo, reason));
            if (refund == null) {
                return null;
            }
            OrderInfo order = orderRepository.findByOrderNo(orderNo);
            executeChannelRefund(order, refund);
            return null;
        });
    }

    @Override
    public void duplicatePayment(String paymentNo) {
        doExceptionPaymentRefund(paymentNo, RefundType.DUPLICATE_PAYMENT, "SYSTEM_DUPLICATE",
            "重复支付自动原路退款", "duplicate:refund:");
    }

    @Override
    public void latePayment(String paymentNo) {
        doExceptionPaymentRefund(paymentNo, RefundType.LATE_PAYMENT, "SYSTEM_LATE",
            "订单关闭后晚到支付自动原路退款", "late:refund:");
    }

    private void doExceptionPaymentRefund(String paymentNo, RefundType refundType, String applyType,
                                          String reason, String lockPrefix) {
        if (paymentNo == null || paymentNo.trim().isEmpty()) {
            log.warn("异常支付冲正缺少支付单号，忽略，type={}", refundType.getType());
            return;
        }
        lock.execute(lockPrefix + paymentNo, 5000L, -1L, () -> {
            // 事务外预检：普通读即可，并发权威守卫是下方事务内 freezeChannelRefund 的 CAS 条件更新。
            PaymentOrder paymentOrder = paymentOrderRepository.findByPaymentNo(paymentNo);
            if (paymentOrder == null) {
                log.warn("异常支付冲正未找到支付单，忽略，paymentNo={}", paymentNo);
                return null;
            }
            if (!RefundApprovalStatus.APPROVED.getType().equals(paymentOrder.getStatus())
                && !"SUCCESS".equals(paymentOrder.getStatus())) {
                log.warn("支付单非成功状态，无需冲正，paymentNo={}, status={}", paymentNo, paymentOrder.getStatus());
                return null;
            }
            Integer refundable = safeNullable(paymentOrder.getPaidAmount())
                - safeNullable(paymentOrder.getRefundedAmount())
                - safeNullable(paymentOrder.getRefundFrozenAmount());
            if (refundable == null || refundable <= 0) {
                log.info("支付单无可冲正余额，幂等返回，paymentNo={}", paymentNo);
                return null;
            }

            RefundInfo refund = tx.execute(s -> {
                RefundOrder exists = refundOrderRepository.findByOrderNoAndApplyType(paymentOrder.getOrderNo(), applyType);
                if (exists != null) {
                    log.info("异常冲正退款单已存在，幂等返回，paymentNo={}, refundNo={}", paymentNo, exists.getRefundNo());
                    return refundInfoRepository.findByRefundNo(exists.getRefundNo());
                }
                OrderInfo order = orderRepository.findByOrderNo(paymentOrder.getOrderNo());
                if (order == null) {
                    throw new BizException("异常冲正对应订单不存在，orderNo=" + paymentOrder.getOrderNo());
                }
                // 渠道资金防线：冻结本次冲正金额（paid - refunded - frozen >= amount）。
                if (paymentOrderRepository.freezeChannelRefund(paymentNo, refundable) == 0) {
                    throw new BizException("渠道资金冲正冻结失败，paymentNo=" + paymentNo);
                }
                return createSystemRefund(order, refundType, applyType, reason, refundable, paymentNo);
            });
            if (refund == null) {
                return null;
            }

            OrderInfo order = orderRepository.findByOrderNo(paymentOrder.getOrderNo());
            executeChannelRefund(order, refund);
            // 渠道退款成功后结转：冻结 → 已退。
            if (paymentOrderRepository.settleChannelRefund(paymentNo, refund.getRefund()) == 0) {
                log.error("渠道资金冲正结转失败，需人工核查，paymentNo={}, amount={}", paymentNo, refund.getRefund());
            } else {
                log.info("异常支付冲正完成，paymentNo={}, refundNo={}, amount={}",
                    paymentNo, refund.getRefundNo(), refund.getRefund());
            }
            return null;
        });
    }

    private RefundInfo prepareOversold(String orderNo, String reason) {
        OrderInfo order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        RefundOrder exists = refundOrderRepository.findByOrderNoAndApplyType(orderNo, "SYSTEM_OVERSOLD");
        if (exists != null) {
            return refundInfoRepository.findByRefundNo(exists.getRefundNo());
        }
        orderRepository.updateLegacyStatus(orderNo, OrderStatus.OVER_SOLD_CLOSED.getType());
        RefundInfo refund = createSystemRefund(order, RefundType.CANCEL_BEFORE_SHIP, "SYSTEM_OVERSOLD",
            reason == null ? "支付成功后库存不足自动退款" : reason, order.getTotalFee(), null);
        List<OrderItem> items = orderRepository.listItemsByOrderNo(orderNo);
        for (OrderItem oi : items) {
            RefundItem ri = new RefundItem();
            ri.setRefundOrderId(refund.getId());
            ri.setRefundNo(refund.getRefundNo());
            ri.setOrderItemId(oi.getId());
            ri.setProductId(oi.getProductId());
            ri.setUnitPrice(oi.getUnitPrice());
            ri.setRefundQty(oi.getQuantity());
            ri.setRefundAmount(Math.multiplyExact(oi.getUnitPrice(), oi.getQuantity()));
            ri.setRestockQty(0);
            ri.setLegacyStockReturned(1);
            refundItemRepository.save(ri);
        }
        return refund;
    }

    /**
     * 系统免审退款单 + 渠道退款流水（不写 refund_item、不动订单 refund_status）。
     */
    private RefundInfo createSystemRefund(OrderInfo order, RefundType refundType, String applyType,
                                          String reason, Integer refundAmount, String paymentNo) {
        String refundNo = OrderNoUtils.getRefundNo();
        RefundOrder apply = new RefundOrder();
        apply.setRefundNo(refundNo);
        apply.setOrderNo(order.getOrderNo());
        apply.setUserId(order.getUserId());
        apply.setRefundAmount(refundAmount);
        apply.setRefundType(refundType.getType());
        apply.setPaymentNo(paymentNo);
        apply.setReason(reason);
        apply.setLegacyApplyStatus("ACCEPTED");
        apply.setStatus(RefundOrderStatus.APPROVED.getType());
        apply.setApplyType(applyType);
        apply.setAdminRemark("系统异常冲正免审");
        apply.setAcceptedTime(new Date());
        refundOrderRepository.save(apply);

        RefundInfo refund = new RefundInfo();
        refund.setOrderNo(order.getOrderNo());
        refund.setRefundNo(refundNo);
        refund.setTotalFee(order.getTotalFee());
        refund.setRefund(refundAmount);
        refund.setReason(reason);
        refund.setApprovalStatus(RefundApprovalStatus.APPROVED.getType());
        refund.setApproveRemark("系统异常冲正免审");
        refund.setApprovedTime(new Date());
        refund.setRefundStatus(RefundStatus.CREATED.getType());
        refundInfoRepository.save(refund);
        return refund;
    }

    private void executeChannelRefund(OrderInfo order, RefundInfo refund) {
        String paymentType = order.getPaymentType();
        if (PayType.WXPAY.getType().equals(paymentType)) {
            wxPayRefundFacade.executeRefund(refund);
        } else if (PayType.ALIPAY.getType().equals(paymentType)) {
            aliPayService.executeRefund(refund);
        } else {
            throw new BizException("不支持的支付类型：" + paymentType);
        }
    }

    private int safeNullable(Integer value) {
        return value == null ? 0 : value;
    }
}
