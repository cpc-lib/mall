package cc.ivera.product.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 库存流水（记录型，只读/追加）：每次预占/提交/释放/回补落一条，biz_no 唯一保证幂等。
 * 管理员手工调整失败也落一条（operation_status=FAILED，库存不变化）。
 */
@Data
public class InventoryTransaction {

    private Long id;

    private Date createTime;

    private Date updateTime;

    private String bizNo;//幂等业务号：TYPE:orderNo:itemId 或 REFUND_RESTOCK:refundNo:itemId 或 MANUAL_ADJUST:productId:UUID

    private String bizType;//ORDER_RESERVE/ORDER_COMMIT/ORDER_SOLD/ORDER_RELEASE/REFUND_RESTOCK/REFUND_LOST/MANUAL_ADJUST

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
