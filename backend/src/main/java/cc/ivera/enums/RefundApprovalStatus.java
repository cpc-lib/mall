package cc.ivera.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum RefundApprovalStatus {

    /**
     * 待审核
     */
    PENDING("PENDING"),

    /**
     * 审核通过
     */
    APPROVED("APPROVED"),

    /**
     * 审核拒绝
     */
    REJECTED("REJECTED");

    private final String type;
}
