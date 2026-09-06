package cc.ivera.enums.bill;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 对账差异单处理状态。
 */
@AllArgsConstructor
@Getter
public enum BillDiscrepancyStatus {

    /**
     * 待处理
     */
    OPEN("OPEN", "待处理"),

    /**
     * 已处理（管理员人工核销）
     */
    RESOLVED("RESOLVED", "已处理");

    private final String type;

    private final String description;
}
