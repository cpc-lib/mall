package cc.ivera.enums.bill;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 渠道账单类型。本期仅支持微信交易账单。
 */
@AllArgsConstructor
@Getter
public enum BillType {

    /**
     * 交易账单（含支付行与退款行）
     */
    TRADE("TRADE", "交易账单");

    private final String type;

    private final String description;
}
