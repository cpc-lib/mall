package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RefundStatus {

    /**
     * 已创建退款申请，待审核/待执行
     */
    CREATED("CREATED"),

    /**
     * 退款处理中
     */
    PROCESSING("PROCESSING"),

    /**
     * 退款成功
     */
    SUCCESS("SUCCESS"),

    /**
     * 退款失败
     */
    FAILED("FAILED"),

    /**
     * 退款关闭
     */
    CLOSED("CLOSED"),

    /**
     * 退款异常
     */
    ABNORMAL("ABNORMAL");

    private final String type;
}
