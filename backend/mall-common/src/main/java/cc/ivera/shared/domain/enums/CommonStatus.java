package cc.ivera.shared.domain.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用启停状态，用于商品、用户、支付应用、支付渠道等实体的 status 字段。
 */
@AllArgsConstructor
@Getter
public enum CommonStatus {

    ENABLED("ENABLED"),

    DISABLED("DISABLED");

    private final String type;
}
