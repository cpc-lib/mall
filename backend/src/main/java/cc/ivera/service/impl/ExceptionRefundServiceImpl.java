package cc.ivera.service.impl;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.PaymentOrder;
import cc.ivera.entity.RefundInfo;
import cc.ivera.enums.RefundApprovalStatus;
import cc.ivera.enums.RefundOrderStatus;
import cc.ivera.enums.RefundStatus;
import cc.ivera.enums.RefundType;
import cc.ivera.exception.BizException;
import cc.ivera.lock.DistributedLockTemplate;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.mapper.OrderItemMapper;
import cc.ivera.mapper.PaymentOrderMapper;
import cc.ivera.mapper.RefundInfoMapper;
import cc.ivera.mapper.RefundItemMapper;
import cc.ivera.mapper.RefundOrderMapper;
import cc.ivera.entity.RefundItem;
import cc.ivera.entity.RefundOrder;
import cc.ivera.service.AliPayService;
import cc.ivera.service.ExceptionRefundService;
import cc.ivera.service.wxpay.WxPayRefundFacade;
import cc.ivera.util.OrderNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
 *   系统免审创建 RefundOrder（refund_type=DUPLICATE_PAYMENT/LATE_PAYMENT），
 *   不冻结订单售后额度、不写 refund_item、不动订单 refund_status，
 *   仅在 PaymentOrder 渠道资金防线内（freeze→refund→settle）完成原路退款。
 */
@Service
@Slf4j
public class ExceptionRefundServiceImpl implements ExceptionRefundService {

    private final DistributedLockTemplate lock;
    private final TransactionTemplate tx;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper itemMapper;
    private final RefundOrderMapper applyMapper;
    private final RefundItemMapper refundItemMapper;
    private final RefundInfoMapper refundInfoMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final AliPayService aliPayService;
    private final WxPayRefundFacade wxPayRefundFacade;

    public ExceptionRefundServiceImpl(DistributedLockTemplate lock,
                                      TransactionTemplate tx,
                                      OrderInfoMapper orderMapper,
                                      OrderItemMapper itemMapper,
                                      RefundOrderMapper applyMapper,
                                      RefundItemMapper refundItemMapper,
                                      RefundInfoMapper refundInfoMapper,
                                      PaymentOrderMapper paymentOrderMapper,
                                      // @Lazy 打断 aliPayServiceImpl → paymentSuccessServiceImpl → 本类 → aliPayServiceImpl 构造器循环
                                      @Lazy AliPayService aliPayService,
                                      WxPayRefundFacade wxPayRefundFacade) {
        this.lock = lock;
        this.tx = tx;
        this.orderMapper = orderMapper;
        this.itemMapper = itemMapper;
        this.applyMapper = applyMapper;
        this.refundItemMapper = refundItemMapper;
        this.refundInfoMapper = refundInfoMapper;
        this.paymentOrderMapper = paymentOrderMapper;
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
            OrderInfo order = orderMapper.selectOne(new QueryWrapper<OrderInfo>().eq("order_no", orderNo));
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
            PaymentOrder paymentOrder = paymentOrderMapper.selectByPaymentNoForUpdate(paymentNo);
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
                RefundOrder exists = applyMapper.selectOne(new QueryWrapper<RefundOrder>()
                        .eq("order_no", paymentOrder.getOrderNo())
                        .eq("apply_type", applyType));
                if (exists != null) {
                    log.info("异常冲正退款单已存在，幂等返回，paymentNo={}, refundNo={}", paymentNo, exists.getRefundNo());
                    return refundInfoMapper.selectOne(new QueryWrapper<RefundInfo>()
                            .eq("refund_no", exists.getRefundNo()));
                }
                OrderInfo order = orderMapper.selectOne(new QueryWrapper<OrderInfo>()
                        .eq("order_no", paymentOrder.getOrderNo()));
                if (order == null) {
                    throw new BizException("异常冲正对应订单不存在，orderNo=" + paymentOrder.getOrderNo());
                }
                // 渠道资金防线：冻结本次冲正金额（paid - refunded - frozen >= amount）。
                if (paymentOrderMapper.freezeChannelRefund(paymentNo, refundable) == 0) {
                    throw new BizException("渠道资金冲正冻结失败，paymentNo=" + paymentNo);
                }
                return createSystemRefund(order, refundType, applyType, reason, refundable, paymentNo);
            });
            if (refund == null) {
                return null;
            }

            OrderInfo order = orderMapper.selectOne(new QueryWrapper<OrderInfo>()
                    .eq("order_no", paymentOrder.getOrderNo()));
            executeChannelRefund(order, refund);
            // 渠道退款成功后结转：冻结 → 已退。
            if (paymentOrderMapper.settleChannelRefund(paymentNo, refund.getRefund()) == 0) {
                log.error("渠道资金冲正结转失败，需人工核查，paymentNo={}, amount={}", paymentNo, refund.getRefund());
            } else {
                log.info("异常支付冲正完成，paymentNo={}, refundNo={}, amount={}",
                        paymentNo, refund.getRefundNo(), refund.getRefund());
            }
            return null;
        });
    }

    private RefundInfo prepareOversold(String orderNo, String reason) {
        OrderInfo order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        QueryWrapper<RefundOrder> existsQ = new QueryWrapper<>();
        existsQ.eq("order_no", orderNo).eq("apply_type", "SYSTEM_OVERSOLD");
        RefundOrder exists = applyMapper.selectOne(existsQ);
        if (exists != null) {
            return refundInfoMapper.selectOne(new QueryWrapper<RefundInfo>().eq("refund_no", exists.getRefundNo()));
        }
        order.setLegacyStatus(cc.ivera.enums.OrderStatus.OVER_SOLD_CLOSED.getType());
        orderMapper.updateById(order);
        RefundInfo refund = createSystemRefund(order, RefundType.CANCEL_BEFORE_SHIP, "SYSTEM_OVERSOLD",
                reason == null ? "支付成功后库存不足自动退款" : reason, order.getTotalFee(), null);
        List<cc.ivera.entity.OrderItem> items = itemMapper.selectList(
                new QueryWrapper<cc.ivera.entity.OrderItem>().eq("order_no", orderNo));
        for (cc.ivera.entity.OrderItem oi : items) {
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
            refundItemMapper.insert(ri);
        }
        return refund;
    }

    /** 系统免审退款单 + 渠道退款流水（不写 refund_item、不动订单 refund_status）。 */
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
        applyMapper.insert(apply);

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
        refundInfoMapper.insert(refund);
        return refund;
    }

    private void executeChannelRefund(OrderInfo order, RefundInfo refund) {
        String paymentType = order.getPaymentType();
        if (cc.ivera.enums.PayType.WXPAY.getType().equals(paymentType)) {
            wxPayRefundFacade.executeRefund(refund);
        } else if (cc.ivera.enums.PayType.ALIPAY.getType().equals(paymentType)) {
            aliPayService.executeRefund(refund);
        } else {
            throw new BizException("不支持的支付类型：" + paymentType);
        }
    }

    private int safeNullable(Integer value) {
        return value == null ? 0 : value;
    }
}
