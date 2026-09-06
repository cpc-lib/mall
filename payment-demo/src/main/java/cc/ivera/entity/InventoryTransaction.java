package cc.ivera.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 库存流水（V2）：每次预占/提交/释放/回补落一条，biz_no 唯一保证幂等。
 * V4 起：管理员手工调整失败申请也落一条（operation_status=FAILED，库存不变化）。
 */
@Data
@TableName("t_inventory_transaction")
public class InventoryTransaction extends BaseEntity {

    private String bizNo;//幂等业务号：TYPE:orderNo:itemId 或 REFUND_RESTOCK:refundNo:itemId 或 MANUAL_ADJUST:productId:UUID

    private String bizType;//ORDER_RESERVE/ORDER_COMMIT/ORDER_RELEASE/REFUND_RESTOCK/MANUAL_ADJUST

    private String orderNo;//关联订单号

    private Long orderItemId;//关联订单明细id

    private String refundNo;//关联退款单号（回补时）

    private Long productId;//商品id（库存单元）

    private Integer availableDelta;//可用库存变化量（失败申请为 0）

    private Integer lockedDelta;//锁定库存变化量

    private Integer soldDelta;//已售库存变化量

    private String operationStatus;//SUCCESS-已生效，FAILED-调整申请被拒绝

    private String errorMessage;//失败原因（FAILED 时记录申请调整量与拒绝原因）
}
