package cc.ivera.bill.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 微信交易账单种类（依据微信支付 v3《交易账单详细说明》）。
 * 三种账单明细表头列数与列序各不相同，解析器按表头列名自适应；
 * 对账范围按种类收窄：ALL 对支付与退款，SUCCESS 仅对支付，REFUND 仅对退款。
 */
@AllArgsConstructor
@Getter
public enum BillKind {

    /**
     * 全部账单：含支付成功行(SUCCESS)、退款行(REFUND)、付款码撤销行(REVOKED)
     */
    ALL("ALL", "全部账单"),

    /**
     * 支付成功账单：仅支付成功行
     */
    SUCCESS("SUCCESS", "支付成功账单"),

    /**
     * 退款账单：仅退款行（含退款申请/成功时间列）
     */
    REFUND("REFUND", "退款账单");

    private final String type;

    private final String description;

    /**
     * 解析账单种类 code，未知种类默认按 ALL 处理（全量对账）。
     */
    public static BillKind of(String code) {
        for (BillKind kind : values()) {
            if (kind.type.equalsIgnoreCase(code)) {
                return kind;
            }
        }
        return ALL;
    }
}
