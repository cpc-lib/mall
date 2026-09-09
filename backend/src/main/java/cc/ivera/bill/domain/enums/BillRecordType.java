package cc.ivera.bill.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final Map<String, String> DESCRIPTION_MAP = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(BillRecordType::getType, BillRecordType::getDescription))
    );

    public static String descriptionOf(String type) {
        return type == null ? null : DESCRIPTION_MAP.getOrDefault(type, type);
    }
}
