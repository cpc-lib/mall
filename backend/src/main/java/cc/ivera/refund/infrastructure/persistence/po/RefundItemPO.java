package cc.ivera.refund.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 退款明细 PO：t_refund_item（V2）。
 * restock_qty 记录实际补库存数量；是否补库存取决于退款类型与履约状态。
 */
@Data
@TableName("t_refund_item")
public class RefundItemPO extends BaseEntity {
    private Long refundOrderId;//退款单id
    private String refundNo;
    private Long orderItemId;
    private Long productId;
    private Integer unitPrice;
    private Integer refundQty;//本次退款数量
    private Integer refundAmount;//本次退款金额(分)
    private Integer restockQty;//已补回库存数量
    private Integer legacyStockReturned;//V1 旧已回补标记（0/1），仅供审计
    private String status;//明细状态（随退款单主状态流转）
}
