package cc.ivera.enums.bill;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 账单导入批次状态。
 */
@AllArgsConstructor
@Getter
public enum BillImportStatus {

    /**
     * 已导入（账单流水已落库，尚未完成对账）
     */
    IMPORTED("IMPORTED", "已导入"),

    /**
     * 已对账（差异单已生成，重复对账将幂等返回该结果）
     */
    RECONCILED("RECONCILED", "已对账"),

    /**
     * 失败（解析或对账异常，允许重新触发对账）
     */
    FAILED("FAILED", "失败");

    private final String type;

    private final String description;
}
