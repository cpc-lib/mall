package cc.ivera.product.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 库存流水 PO：t_inventory_transaction。
 */
@Data
@TableName("t_inventory_transaction")
public class InventoryTransactionPO extends BaseEntity {

    private String bizNo;//幂等业务号：TYPE:orderNo:itemId 或 REFUND_RESTOCK:refundNo:itemId 或 MANUAL_ADJUST:productId:UUID

    private String bizType;//ORDER_RESERVE/ORDER_COMMIT/ORDER_RELEASE/REFUND_RESTOCK/MANUAL_ADJUST

    private String orderNo;//关联订单号

    private Long orderItemId;//关联订单明细id

    private String refundNo;//关联退款单号（回补时）

    private Long productId;//商品id（库存单元）

    private Integer availableDelta;//可用库存变化量（失败申请为 0）

    private Integer lockedDelta;//锁定库存变化量

    private Integer soldDelta;//已售库存变化量

    private Integer lostDelta;//丢失/货损库存变化量（REFUND_LOST 时为正）

    private String operationStatus;//SUCCESS-已生效，FAILED-调整申请被拒绝

    private String errorMessage;//失败原因（FAILED 时记录申请调整量与拒绝原因）
}
