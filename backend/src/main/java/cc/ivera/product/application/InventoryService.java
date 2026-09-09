package cc.ivera.product.application;

import cc.ivera.product.domain.model.RefundStockLine;
import cc.ivera.product.domain.model.ReserveLine;

import java.util.Date;
import java.util.List;

/**
 * 库存域服务（V2）：预占 / 提交 / 释放 / 退款回补。
 * 全部本地事务同步执行，不走 MQ；inventory_transaction.biz_no 唯一保证幂等。
 *
 * <p>退款回补/核销经 RefundStockLine 边界类型接收退款上下文数据，库存域不依赖退款实体。</p>
 */
public interface InventoryService {

    /**
     * 下单预占：按订单明细逐项锁定库存（available-=qty, locked+=qty）。
     * 任一项库存不足抛 BizException，整体事务回滚。
     */
    void reserveForOrder(String orderNo, Date expireTime, List<ReserveLine> lines);

    /**
     * 支付成功提交预占：reservation LOCKED→COMMITTED（CAS）。
     * 库存数量不变（保持锁定），待确认收货时结转已售。
     */
    void commitReservation(String orderNo);

    /**
     * 确认收货结转已售：按订单明细将锁定库存结转为已售库存（locked-=qty, sold+=qty）。
     * 结转数量 = quantity - restockedQty（收货前已回补部分不重复结转）；
     * 以流水 bizNo（ORDER_SOLD:orderNo:itemId）唯一键保证幂等。
     */
    void convertToSoldOnReceipt(String orderNo);

    /**
     * 超时关单/取消释放预占：reservation LOCKED→RELEASED（CAS），locked 归还 available。
     */
    void releaseReservation(String orderNo);

    /**
     * 退款回补：来源桶按订单是否已确认收货分流——
     * 未收货（afterReceipt=false）：locked-=qty, available+=qty；
     * 已收货（afterReceipt=true）：sold-=qty, available+=qty。
     * restocked+qty 不得超过 已退+冻结 上限；是否回补由退款类型策略决定，调用方只传需要回补的明细。
     */
    void restockForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt);

    /**
     * 仅退款核销货损（货物不回仓，计入丢失库存），来源桶按订单是否已确认收货分流——
     * 未收货（afterReceipt=false）：locked-=qty, lost+=qty（货仍在锁定桶）；
     * 已收货（afterReceipt=true）：sold-=qty, lost+=qty（货已结转售出桶，留用户不退回）。
     * restocked_qty 同步累加做超核销守卫；以流水 bizNo（REFUND_LOST:refundNo:itemId）保证幂等。
     */
    void writeOffLostForRefund(String refundNo, List<RefundStockLine> items, boolean afterReceipt);
}
