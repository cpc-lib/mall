package cc.ivera.bill.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 账单对账差异类型。
 * 账账核对差异类型：覆盖单边账、金额/状态不一致、渠道流水串单以及账单重复流水。
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
     * 支付：同一订单能定位到平台支付单，但渠道交易号与平台记录不一致
     */
    PAY_SERIAL_MISMATCH("PAY_SERIAL_MISMATCH", BillRecordType.PAY, "支付渠道流水号不一致"),

    /**
     * 支付：相同渠道流水号对应的商户订单号与平台支付单订单号不一致
     */
    PAY_BIZ_NO_MISMATCH("PAY_BIZ_NO_MISMATCH", BillRecordType.PAY, "支付业务单号不一致"),

    /**
     * 支付：渠道账单中同一微信订单号重复出现
     */
    PAY_CHANNEL_DUPLICATE("PAY_CHANNEL_DUPLICATE", BillRecordType.PAY, "渠道支付流水重复"),

    /**
     * 支付：平台账中同一渠道交易号出现多笔成功支付单
     */
    PAY_LOCAL_DUPLICATE("PAY_LOCAL_DUPLICATE", BillRecordType.PAY, "平台支付流水重复"),

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
    REFUND_STATUS_MISMATCH("REFUND_STATUS_MISMATCH", BillRecordType.REFUND, "退款状态不一致"),

    /**
     * 退款：渠道账单中同一商户退款单号重复出现
     */
    REFUND_CHANNEL_DUPLICATE("REFUND_CHANNEL_DUPLICATE", BillRecordType.REFUND, "渠道退款流水重复");

    private static final Map<String, String> DESCRIPTION_MAP = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(BillDiscrepancyType::getType, BillDiscrepancyType::getDescription))
    );
    private final String type;
    private final BillRecordType bizType;
    private final String description;

    public static String descriptionOf(String type) {
        return type == null ? null : DESCRIPTION_MAP.getOrDefault(type, type);
    }
}
