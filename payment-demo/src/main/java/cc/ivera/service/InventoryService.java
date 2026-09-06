package cc.ivera.service;

import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderItem;
import cc.ivera.entity.RefundItem;

import java.util.List;

/**
 * 库存域服务（V2）：预占 / 提交 / 释放 / 退款回补。
 * 全部本地事务同步执行，不走 MQ；inventory_transaction.biz_no 唯一保证幂等。
 */
public interface InventoryService {

    /**
     * 下单预占：按订单明细逐项锁定库存（available-=qty, locked+=qty）。
     * 任一项库存不足抛 BizException，整体事务回滚。
     */
    void reserveForOrder(OrderInfo order, List<OrderItem> items);

    /**
     * 支付成功提交预占：reservation LOCKED→COMMITTED（CAS），locked-=qty。
     */
    void commitReservation(String orderNo);

    /**
     * 超时关单/取消释放预占：reservation LOCKED→RELEASED（CAS），locked 归还 available。
     */
    void releaseReservation(String orderNo);

    /**
     * 退款回补：available+=qty；restocked+qty 不得超过 已退+冻结 上限。
     * 是否回补由退款类型策略决定，调用方只传需要回补的明细。
     */
    void restockForRefund(String refundNo, List<RefundItem> items);
}
