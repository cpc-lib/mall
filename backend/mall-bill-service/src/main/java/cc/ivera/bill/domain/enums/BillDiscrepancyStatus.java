package cc.ivera.bill.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

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

    private static final Map<String, String> DESCRIPTION_MAP = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(BillDiscrepancyStatus::getType, BillDiscrepancyStatus::getDescription))
    );
    private final String type;
    private final String description;

    public static String descriptionOf(String type) {
        return type == null ? null : DESCRIPTION_MAP.getOrDefault(type, type);
    }
}
