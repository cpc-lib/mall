package cc.ivera.bill.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final Map<String, String> DESCRIPTION_MAP = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(BillImportStatus::getType, BillImportStatus::getDescription))
    );
    private final String type;
    private final String description;

    public static String descriptionOf(String type) {
        return type == null ? null : DESCRIPTION_MAP.getOrDefault(type, type);
    }
}
