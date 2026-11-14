package cc.ivera.order.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 订单明细 PO：t_order_item（下单成交快照 + 退款冻结/已退计数器）。
 */
@Data
@TableName("t_order_item")
public class OrderItemPO extends BaseEntity {

    private Long orderId;

    private String orderNo;

    private Long productId;

    private String productTitle;

    private Integer unitPrice;

    private Integer quantity;

    private Integer dealUnitAmount;//成交单价快照（结构预留优惠分摊，当前=unitPrice）

    private Integer originalTotalAmount;//原始小计=unit_price*quantity

    private Integer discountAmount;//分摊优惠金额，当前恒为0

    private Integer payAmount;//该订单行实际承担支付金额，退款资金上限

    private Integer refundedQty;//已退款数量

    private Integer refundFrozenQty;//退款申请冻结数量

    private Integer refundFrozenAmount;//退款申请冻结金额(分)

    private Integer refundedAmount;//已退款金额(分)

    private Integer restockedQty;//已补回库存数量
}
