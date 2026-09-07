package cc.ivera.enums.bill;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账单对账差异类型。
 * 支付核对（PAY）与退款核对（REFUND）各四类：渠道有本地无、本地有渠道无、金额不一致、状态不一致。
 */
@AllArgsConstructor
@Getter
public enum BillDiscrepancyType {

    /**
     * 支付：账单有支付记录，本地支付流水不存在
     */
    PAY_CHANNEL_ONLY("PAY_CHANNEL_ONLY", BillRecordType.PAY, "渠道有支付，本地无记录"),

    /**
     * 支付：本地有成功支付，账单无对应记录
     */
    PAY_LOCAL_ONLY("PAY_LOCAL_ONLY", BillRecordType.PAY, "本地有支付，渠道无记录"),

    /**
     * 支付：账单与本地金额不一致
     */
    PAY_AMOUNT_MISMATCH("PAY_AMOUNT_MISMATCH", BillRecordType.PAY, "支付金额不一致"),

    /**
     * 支付：账单支付成功，本地订单/流水非成功终态
     */
    PAY_STATUS_MISMATCH("PAY_STATUS_MISMATCH", BillRecordType.PAY, "支付状态不一致"),

    /**
     * 退款：账单有退款记录，本地退款单不存在
     */
    REFUND_CHANNEL_ONLY("REFUND_CHANNEL_ONLY", BillRecordType.REFUND, "渠道有退款，本地无记录"),

    /**
     * 退款：本地有成功退款，账单无对应记录
     */
    REFUND_LOCAL_ONLY("REFUND_LOCAL_ONLY", BillRecordType.REFUND, "本地有退款，渠道无记录"),

    /**
     * 退款：账单与本地退款金额不一致
     */
    REFUND_AMOUNT_MISMATCH("REFUND_AMOUNT_MISMATCH", BillRecordType.REFUND, "退款金额不一致"),

    /**
     * 退款：账单退款成功，本地退款单非成功状态
     */
    REFUND_STATUS_MISMATCH("REFUND_STATUS_MISMATCH", BillRecordType.REFUND, "退款状态不一致");

    private final String type;

    private final BillRecordType bizType;

    private final String description;
}
