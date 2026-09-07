package cc.ivera.enums.bill;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账单流水记录类型：微信交易账单中的支付行与退款行。
 */
@AllArgsConstructor
@Getter
public enum BillRecordType {

    /**
     * 支付记录
     */
    PAY("PAY", "支付"),

    /**
     * 退款记录
     */
    REFUND("REFUND", "退款");

    private final String type;

    private final String description;
}
