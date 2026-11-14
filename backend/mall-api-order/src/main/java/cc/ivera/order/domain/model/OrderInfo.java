package cc.ivera.order.domain.model;

import cc.ivera.order.domain.enums.FulfillmentStatus;
import cc.ivera.order.domain.enums.OrderLifecycleStatus;
import cc.ivera.order.domain.enums.OrderRefundStatus;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.payment.domain.enums.PayStatus;
import cc.ivera.shared.domain.exception.BizException;
import lombok.Data;

import java.util.Date;

/**
 * 订单聚合根（t_order_info）：交易/支付/履约/退款四维状态 + V1 legacy 审计状态。
 *
 * <p>状态推进的并发权威闸门为仓储 CAS 条件更新（行锁/legacy_status CAS），
 * 本类承载状态规则与守卫；状态流转一律先经守卫再走 CAS，禁止先改后查。</p>
 */
@Data
public class OrderInfo {

    private Long id;

    private String title;//订单标题

    private String orderNo;//商户订单编号

    private Long userId;//用户id（V1 快速购买入口为空）

    private Long productId;//支付产品id（兼容旧字段，真实商品组成以 OrderItem 为准）

    private Integer totalFee;//订单金额(分)

    private String codeUrl;//订单二维码连接

    private String legacyStatus;//V1 旧单一状态值（NOTPAY/SUCCESS/...），仅供审计/回滚对照

    private String orderStatus;//交易状态：WAIT_PAY/ACTIVE/CLOSED/COMPLETED

    private String payStatus;//支付状态：UNPAID/PAID

    private String fulfillmentStatus;//履约状态：WAIT_SHIP/SHIPPED/RECEIVED/CANCELLED

    private String refundStatus;//退款汇总状态：NONE/REFUNDING/PARTIAL_REFUNDED/FULL_REFUNDED

    private Integer paidAmount;//有效实付金额(分)

    private Integer refundFrozenAmount;//退款申请冻结金额(分)

    private Integer refundedAmount;//已成功退款金额(分)

    private Date expireTime;//订单过期时间（超时关单/库存预占释放边界）

    private Date paidTime;//支付成功时间

    private String receiverName;//收货人姓名（物流模拟）

    private String receiverPhone;//收货人电话（物流模拟）

    private String receiverAddress;//收货地址（物流模拟）

    private String paymentType;//支付方式

    private Long paymentAppId;//支付应用ID

    private String paymentChannelCode;//支付渠道编码

    private Integer version;//乐观锁版本号

    private Date createTime;

    private Date updateTime;
    /**
     * 非持久化补丁标记：CAS 更新时是否追加 paid_amount = total_fee（仅服务层补丁对象使用）。
     */
    private transient boolean applyPaidAmountFromTotalFee;

    /**
     * 新建待支付订单工厂：四维状态显式初始化（与 DDL 默认值一致），legacy=NOTPAY。
     * 快速购买（无用户/无收货输入）userId 与收货信息允许为空，由调用方按入口规则填充。
     */
    public static OrderInfo createNew(String title, String orderNo, Long userId, Long productId, Integer totalFee,
                                      String paymentType, Long paymentAppId, String paymentChannelCode,
                                      Date expireTime, String receiverName, String receiverPhone, String receiverAddress) {
        OrderInfo order = new OrderInfo();
        order.setTitle(title);
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setProductId(productId);
        order.setTotalFee(totalFee);
        order.setLegacyStatus(OrderStatus.NOTPAY.getType());
        order.setPaymentType(paymentType);
        order.setPaymentAppId(paymentAppId);
        order.setPaymentChannelCode(paymentChannelCode);
        order.setVersion(0);
        order.setExpireTime(expireTime);
        order.setOrderStatus(OrderLifecycleStatus.WAIT_PAY.getType());
        order.setPayStatus(PayStatus.UNPAID.getType());
        order.setFulfillmentStatus(FulfillmentStatus.WAIT_SHIP.getType());
        order.setRefundStatus(OrderRefundStatus.NONE.getType());
        order.setReceiverName(receiverName);
        order.setReceiverPhone(receiverPhone);
        order.setReceiverAddress(receiverAddress);
        return order;
    }

    /**
     * legacy 状态条件更新补丁工厂：按目标 legacy 状态同步 V2 四维字段。
     * SUCCESS → 交易 ACTIVE + 支付 PAID + 支付时间（仓储 CAS 同时落 paid_amount=total_fee）；
     * CLOSED/CANCEL → 交易 CLOSED。
     */
    public static OrderInfo legacyStatusPatch(OrderStatus target, Date now) {
        OrderInfo patch = new OrderInfo();
        patch.setLegacyStatus(target.getType());
        if (target == OrderStatus.SUCCESS) {
            patch.setOrderStatus(OrderLifecycleStatus.ACTIVE.getType());
            patch.setPayStatus(PayStatus.PAID.getType());
            patch.setPaidTime(now);
            patch.applyPaidAmountFromTotalFee = true;
        } else if (target == OrderStatus.CLOSED || target == OrderStatus.CANCEL) {
            patch.setOrderStatus(OrderLifecycleStatus.CLOSED.getType());
        }
        return patch;
    }

    /**
     * 是否可发起支付：仅 V1 legacy NOTPAY 状态可支付。
     */
    public boolean isPayable() {
        return OrderStatus.NOTPAY.getType().equals(legacyStatus);
    }

    /**
     * V1 兼容视图状态（订单详情输出装配用）：refund_status 非 NONE 优先映射旧退款值，
     * 其次按交易生命周期映射。老前端按 V1 中文状态值渲染与禁用按钮；
     * payStatus/fulfillmentStatus/refundStatus 等新字段原样透出。
     */
    public String toLegacyViewStatus() {
        if (OrderRefundStatus.REFUNDING.getType().equals(refundStatus)) {
            return OrderStatus.REFUND_PROCESSING.getType();
        }
        if (OrderRefundStatus.PARTIAL_REFUNDED.getType().equals(refundStatus)) {
            return OrderStatus.PARTIAL_REFUND.getType();
        }
        if (OrderRefundStatus.FULL_REFUNDED.getType().equals(refundStatus)) {
            return OrderStatus.REFUND_SUCCESS.getType();
        }
        if (OrderLifecycleStatus.CLOSED.getType().equals(orderStatus)) {
            return OrderStatus.CLOSED.getType();
        }
        if (orderStatus == null || OrderLifecycleStatus.WAIT_PAY.getType().equals(orderStatus)) {
            return OrderStatus.NOTPAY.getType();
        }
        return OrderStatus.SUCCESS.getType();
    }

    /**
     * 发货前置守卫（与物流单存在性检查共同构成发货规则）：
     * 已支付、无退款、未收货、处于待发货，且不存在已创建物流单。
     */
    public void requireShippable(boolean shipmentExists) {
        if (!PayStatus.PAID.getType().equals(payStatus)) {
            throw new BizException("订单未支付，不能发货，orderNo=" + orderNo);
        }
        if (refundStatus != null && !OrderRefundStatus.NONE.getType().equals(refundStatus)) {
            throw new BizException("订单已退款或退款中，不可发货，refundStatus=" + refundStatus);
        }
        if (FulfillmentStatus.RECEIVED.getType().equals(fulfillmentStatus)) {
            throw new BizException("订单已确认收货，orderNo=" + orderNo);
        }
        if (!FulfillmentStatus.WAIT_SHIP.getType().equals(fulfillmentStatus)) {
            throw new BizException("订单当前履约状态不可发货：" + fulfillmentStatus);
        }
        if (shipmentExists) {
            throw new BizException("订单已存在物流单，不能重复发货，orderNo=" + orderNo);
        }
    }

    /**
     * 确认收货前置守卫：存在退款（汇总状态非 NONE）不允许确认收货。
     * 物流单是否已送达由 {@link OrderShipment#requireDeliveredForReceipt(String)} 守卫。
     */
    public void requireOpenForReceipt() {
        if (refundStatus != null && !OrderRefundStatus.NONE.getType().equals(refundStatus)) {
            throw new BizException("订单已退款或退款中，不可确认收货，refundStatus=" + refundStatus);
        }
    }

    public boolean isApplyPaidAmountFromTotalFee() {
        return applyPaidAmountFromTotalFee;
    }
}
