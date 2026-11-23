package cc.ivera.refund.application.impl;

import cc.ivera.api.order.OrderQueryApi;
import cc.ivera.api.order.OrderStatusCommandApi;
import cc.ivera.api.payment.PaymentChannelRefundApi;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.payment.application.dto.ChannelRefundCommand;
import cc.ivera.payment.application.dto.ChannelRefundResult;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.api.payment.PaymentOrderCommandApi;
import cc.ivera.api.payment.PaymentOrderQueryApi;
import cc.ivera.refund.application.ExceptionRefundService;
import cc.ivera.refund.application.RefundInfoService;
import cc.ivera.refund.application.RefundStatusSyncMessageService;
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
    private final OrderQueryApi orderQueryApi;
    private final OrderStatusCommandApi orderStatusCommandApi;
    private final RefundOrderRepository refundOrderRepository;
    private final RefundItemRepository refundItemRepository;
    private final RefundInfoRepository refundInfoRepository;
    private final PaymentOrderQueryApi paymentOrderQueryApi;
    private final PaymentOrderCommandApi paymentOrderCommandApi;
    private final PaymentChannelRefundApi paymentChannelRefundApi;
    private final RefundInfoService refundInfoService;
    private final RefundStatusSyncMessageService refundStatusSyncMessageService;

    public ExceptionRefundServiceImpl(DistributedLockTemplate lock,
                                      TransactionTemplate tx,
                                      OrderQueryApi orderQueryApi,
                                      OrderStatusCommandApi orderStatusCommandApi,
                                      RefundOrderRepository refundOrderRepository,
                                      RefundItemRepository refundItemRepository,
                                      RefundInfoRepository refundInfoRepository,
                                      PaymentOrderQueryApi paymentOrderQueryApi,
                                      PaymentOrderCommandApi paymentOrderCommandApi,
                                      PaymentChannelRefundApi paymentChannelRefundApi,
                                      RefundInfoService refundInfoService,
                                      RefundStatusSyncMessageService refundStatusSyncMessageService) {
        this.lock = lock;
        this.tx = tx;
        this.orderQueryApi = orderQueryApi;
        this.orderStatusCommandApi = orderStatusCommandApi;
        this.refundOrderRepository = refundOrderRepository;
        this.refundItemRepository = refundItemRepository;
        this.refundInfoRepository = refundInfoRepository;
        this.paymentOrderQueryApi = paymentOrderQueryApi;
        this.paymentOrderCommandApi = paymentOrderCommandApi;
        this.paymentChannelRefundApi = paymentChannelRefundApi;
        this.refundInfoService = refundInfoService;
        this.refundStatusSyncMessageService = refundStatusSyncMessageService;
    }

    @Override
    public void trigger(String orderNo, String reason) {
        // 退款申请创建按订单号串行化，与用户端 create/cancelPaidOrder 共用同一把锁，避免同一订单并发创建多笔退款。
        lock.execute("refund:create:" + orderNo, 5000L, () -> {
            RefundInfo refund = tx.execute(s -> prepareOversold(orderNo, reason));
            if (refund == null) {
                return null;
            }
            OrderInfo order = orderQueryApi.findByOrderNo(orderNo);
            executeChannelRefund(order, refund, null);
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
        lock.execute(lockPrefix + paymentNo, 5000L, () -> {
            // 事务外预检：普通读即可，并发权威守卫是下方事务内 freezeChannelRefund 的 CAS 条件更新。
            PaymentOrder paymentOrder = paymentOrderQueryApi.findByPaymentNo(paymentNo);
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
                RefundOrder exists = refundOrderRepository.findByPaymentNoAndApplyType(paymentNo, applyType);
                if (exists != null) {
                    // 已存在退款单：仅 SUCCESS 时真正幂等返回；
                    // PROCESSING/FAILED 继续走渠道退款——渠道幂等键（微信 out_refund_no / 支付宝 refund_id）保证不会重复退款，
                    // 成功后将 settleChannelRefund 把第一次调用冻结的资金结转
                    if (RefundOrderStatus.SUCCESS.getType().equals(exists.getStatus())) {
                        log.info("异常冲正已成功，幂等返回，paymentNo={}, refundNo={}", paymentNo, exists.getRefundNo());
                        return null;
                    }
                    log.info("异常冲正退款单已存在但未完成，继续渠道幂等调用，paymentNo={}, refundNo={}, status={}",
                        paymentNo, exists.getRefundNo(), exists.getStatus());
                    return refundInfoRepository.findByRefundNo(exists.getRefundNo());
                }
                OrderInfo order = orderQueryApi.findByOrderNo(paymentOrder.getOrderNo());
                if (order == null) {
                    throw new BizException("异常冲正对应订单不存在，orderNo=" + paymentOrder.getOrderNo());
                }
                // 渠道资金防线：冻结本次冲正金额（paid - refunded - frozen >= amount）。
                if (paymentOrderCommandApi.freezeChannelRefund(paymentNo, refundable) == 0) {
                    throw new BizException("渠道资金冲正冻结失败，paymentNo=" + paymentNo);
                }
                return createSystemRefund(order, refundType, applyType, reason, refundable, paymentNo);
            });
            if (refund == null) {
                return null;
            }

            OrderInfo order = orderQueryApi.findByOrderNo(paymentOrder.getOrderNo());
            executeChannelRefund(order, refund, paymentNo);
            // 渠道退款成功后结转：冻结 → 已退。
            if (paymentOrderCommandApi.settleChannelRefund(paymentNo, refund.getRefund()) == 0) {
                log.error("渠道资金冲正结转失败，需人工核查，paymentNo={}, amount={}", paymentNo, refund.getRefund());
            } else {
                log.info("异常支付冲正完成，paymentNo={}, refundNo={}, amount={}",
                    paymentNo, refund.getRefundNo(), refund.getRefund());
            }
            return null;
        });
    }

    private RefundInfo prepareOversold(String orderNo, String reason) {
        OrderInfo order = orderQueryApi.findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        RefundOrder exists = refundOrderRepository.findByOrderNoAndApplyType(orderNo, "SYSTEM_OVERSOLD");
        if (exists != null) {
            return refundInfoRepository.findByRefundNo(exists.getRefundNo());
        }
        orderStatusCommandApi.closeOversold(orderNo);
        RefundInfo refund = createSystemRefund(order, RefundType.CANCEL_BEFORE_SHIP, "SYSTEM_OVERSOLD",
            reason == null ? "支付成功后库存不足自动退款" : reason, order.getTotalFee(), null);
        List<OrderItem> items = orderQueryApi.listItemsByOrderNo(orderNo);
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

    private void executeChannelRefund(OrderInfo order, RefundInfo refund, String paymentNo) {
        ChannelRefundCommand command = new ChannelRefundCommand();
        command.setRefundNo(refund.getRefundNo());
        command.setOrderNo(refund.getOrderNo());
        command.setPaymentNo(paymentNo);
        command.setChannel(order.getPaymentType());
        command.setTotalFee(refund.getTotalFee());
        command.setRefundAmount(refund.getRefund());
        command.setReason(refund.getReason());

        ChannelRefundResult result = paymentChannelRefundApi.executeRefund(command);
        refundInfoService.applyChannelRefundResult(refund.getRefundNo(), result);
        if (result == null || result.getOutcome() == ChannelRefundResult.Outcome.FAILED) {
            throw new BizException(result == null ? "渠道退款结果为空" : result.getErrorMessage());
        }
        if (result.getOutcome() == ChannelRefundResult.Outcome.PROCESSING) {
            refundStatusSyncMessageService.sendRefundStatusSyncMessage(refund.getRefundNo());
        }
    }

    private int safeNullable(Integer value) {
        return value == null ? 0 : value;
    }
}
