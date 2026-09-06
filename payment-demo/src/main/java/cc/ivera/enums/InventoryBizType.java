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

    /** 支付成功提交预占：locked-=qty */
    ORDER_COMMIT("ORDER_COMMIT"),

    /** 关单/取消释放预占：available+=qty, locked-=qty */
    ORDER_RELEASE("ORDER_RELEASE"),

    /** 退款回补：available+=qty */
    REFUND_RESTOCK("REFUND_RESTOCK"),

    /** 管理员手工库存调整（补货/扣减）：available±delta，biz_no = MANUAL_ADJUST:productId:UUID */
    MANUAL_ADJUST("MANUAL_ADJUST");

    /**
     * 类型
     */
    private final String type;
}
