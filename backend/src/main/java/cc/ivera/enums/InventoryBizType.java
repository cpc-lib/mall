package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 库存流水类型（落 t_inventory_transaction.biz_type）。
 * biz_no 唯一约束保证同一动作幂等：TYPE:orderNo:itemId 或 REFUND_RESTOCK:refundNo:itemId。
 */
@AllArgsConstructor
@Getter
public enum InventoryBizType {

    /** 下单预占：available-=qty, locked+=qty */
    ORDER_RESERVE("ORDER_RESERVE"),

    /** 支付成功提交预占：库存数量不变（保持锁定），仅 reservation LOCKED→COMMITTED 事件标记 */
    ORDER_COMMIT("ORDER_COMMIT"),

    /** 确认收货结转已售：locked-=qty, sold+=qty */
    ORDER_SOLD("ORDER_SOLD"),

    /** 关单/取消释放预占：available+=qty, locked-=qty */
    ORDER_RELEASE("ORDER_RELEASE"),

    /** 退款回补：locked/sold 归还 available（来源桶按订单是否已确认收货分流） */
    REFUND_RESTOCK("REFUND_RESTOCK"),

    /** 仅退款未收货核销货损：locked-=qty, lost+=qty（货物不回仓，计入丢失库存） */
    REFUND_LOST("REFUND_LOST"),

    /** 管理员手工库存调整（补货/扣减）：available±delta，biz_no = MANUAL_ADJUST:productId:UUID */
    MANUAL_ADJUST("MANUAL_ADJUST");

    /**
     * 类型
     */
    private final String type;
}
