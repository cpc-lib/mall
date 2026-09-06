package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 退款类型（落 t_refund_order.refund_type，存储英文标识）。
 * 是否补库存：CANCEL_BEFORE_SHIP / RETURN_AND_REFUND 补，其余不补。
 * 是否占用售后额度：DUPLICATE_PAYMENT / LATE_PAYMENT 系统自动冲正，不占额度、不写退款明细。
 */
@AllArgsConstructor
@Getter
public enum RefundType {

    /** 未发货取消（履约必须 WAIT_SHIP，受理后自动补库存） */
    CANCEL_BEFORE_SHIP("CANCEL_BEFORE_SHIP", "未发货取消"),

    /** 退货退款（履约必须 RECEIVED，管理员签收质检后补库存） */
    RETURN_AND_REFUND("RETURN_AND_REFUND", "退货退款"),

    /** 仅退款（履约 SHIPPED/RECEIVED，不补库存） */
    REFUND_ONLY("REFUND_ONLY", "仅退款"),

    /** 差价退款（管理员发起，手填金额受剩余额度约束，不补库存） */
    PRICE_ADJUSTMENT("PRICE_ADJUSTMENT", "差价退款"),

    /** 重复支付自动原路退款（系统，不占售后额度） */
    DUPLICATE_PAYMENT("DUPLICATE_PAYMENT", "重复支付退款"),

    /** 晚到支付自动原路退款（订单已关闭后支付成功，系统，不占售后额度） */
    LATE_PAYMENT("LATE_PAYMENT", "晚到支付退款");

    /**
     * 类型标识（落库值）
     */
    private final String type;

    /**
     * 中文描述
     */
    private final String desc;
}
