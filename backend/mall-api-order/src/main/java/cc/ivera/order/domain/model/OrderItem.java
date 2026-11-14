package cc.ivera.order.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 订单明细实体（订单聚合内）：下单时的商品成交快照。
 * 金额快照（deal_unit_amount/pay_amount）是退款资金上限的计算依据，永远基于下单价而非当前商品价；
 * 退款数量/金额三层冻结字段（已退/冻结）支撑防超退守卫（CAS 在仓储）。
 */
@Data
public class OrderItem {

    private Long id;

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

    private Date createTime;

    private Date updateTime;

    /**
     * 下单成交快照工厂：无优惠券，deal/pay 金额 = unitPrice*quantity，退款计数器归零。
     */
    public static OrderItem createSnapshot(Long orderId, String orderNo, Long productId, String productTitle,
                                           Integer unitPrice, Integer quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setOrderNo(orderNo);
        item.setProductId(productId);
        item.setProductTitle(productTitle);
        item.setUnitPrice(unitPrice);
        item.setQuantity(quantity);
        item.setDealUnitAmount(unitPrice);
        item.setOriginalTotalAmount(Math.multiplyExact(unitPrice, quantity));
        item.setDiscountAmount(0);
        item.setPayAmount(Math.multiplyExact(unitPrice, quantity));
        item.setRefundedQty(0);
        item.setRefundFrozenQty(0);
        item.setRefundFrozenAmount(0);
        item.setRefundedAmount(0);
        item.setRestockedQty(0);
        return item;
    }
}
