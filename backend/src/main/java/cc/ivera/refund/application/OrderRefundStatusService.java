package cc.ivera.refund.application;

import cc.ivera.order.application.OrderInfoService;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;

/**
 * 订单退款汇总状态刷新服务：按渠道退款流水金额优先级回写订单 legacy 退款状态。
 */
@Service
public class OrderRefundStatusService {

    private final OrderInfoService orderInfoService;

    private final RefundInfoRepository refundInfoRepository;

    private final DistributedLockTemplate distributedLockTemplate;

    private final TransactionTemplate transactionTemplate;

    public OrderRefundStatusService(
        OrderInfoService orderInfoService,
        RefundInfoRepository refundInfoRepository,
        DistributedLockTemplate distributedLockTemplate,
        TransactionTemplate transactionTemplate
    ) {
        this.orderInfoService = orderInfoService;
        this.refundInfoRepository = refundInfoRepository;
        this.distributedLockTemplate = distributedLockTemplate;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 刷新订单退款汇总状态。并发互斥靠 Redis 分布式锁（按订单号串行化），
     * 移除底层 FOR UPDATE 后避免锁在 autocommit 下空转；外层事务挂起、独立提交，
     * 即使外层事务回滚，订单状态反映已有退款流水仍然正确，下一轮刷新会修正。
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void refreshOrderRefundStatus(String orderNo) {
        distributedLockTemplate.execute("payment:refund:status:" + orderNo, 5000L, -1L, () ->
            transactionTemplate.execute(status -> {
                doRefreshOrderRefundStatus(orderNo);
                return null;
            })
        );
    }

    private void doRefreshOrderRefundStatus(String orderNo) {
        OrderInfo orderInfo = orderInfoService.getOrderByOrderNo(orderNo);
        if (orderInfo == null) {
            return;
        }
        // 超卖熔断订单必须保留 OVER_SOLD_CLOSED 终态；退款成功只更新退款单，不把订单改成普通“已退款”。
        if (OrderStatus.OVER_SOLD_CLOSED.getType().equals(orderInfo.getLegacyStatus())) {
            return;
        }

        int successRefundAmount = getRefundAmount(orderNo, RefundStatus.SUCCESS);
        int processingRefundAmount = getRefundAmount(orderNo, RefundStatus.PROCESSING);
        int abnormalRefundAmount = getRefundAmount(orderNo, RefundStatus.ABNORMAL);

        OrderStatus targetStatus = determineRefundOrderStatus(
            orderInfo.getTotalFee(), successRefundAmount, processingRefundAmount, abnormalRefundAmount);
        orderInfoService.updateStatusByOrderNo(orderNo, targetStatus);
    }

    /**
     * 按退款金额优先级判定订单退款状态：
     * 全额退成功 → 部分退成功 → 退款处理中 → 退款异常 → 无进行中退款（回到已支付）。
     */
    private OrderStatus determineRefundOrderStatus(int totalFee,
                                                   int successAmount,
                                                   int processingAmount,
                                                   int abnormalAmount) {
        if (successAmount >= totalFee) {
            return OrderStatus.REFUND_SUCCESS;
        }
        if (successAmount > 0) {
            return OrderStatus.PARTIAL_REFUND;
        }
        if (processingAmount > 0) {
            return OrderStatus.REFUND_PROCESSING;
        }
        if (abnormalAmount > 0) {
            return OrderStatus.REFUND_ABNORMAL;
        }
        return OrderStatus.SUCCESS;
    }

    public void refreshOrderRefundStatusByRefundNo(String refundNo) {
        RefundInfo refundInfo = refundInfoRepository.findByRefundNo(refundNo);
        if (refundInfo != null) {
            refreshOrderRefundStatus(refundInfo.getOrderNo());
        }
    }

    private int getRefundAmount(String orderNo, RefundStatus refundStatus) {
        Integer amount = refundInfoRepository.sumRefundAmountByOrderNoAndStatuses(
            orderNo,
            Collections.singletonList(refundStatus.getType())
        );
        return amount == null ? 0 : amount;
    }
}
