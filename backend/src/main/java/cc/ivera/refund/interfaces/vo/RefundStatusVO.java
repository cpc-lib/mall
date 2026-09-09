package cc.ivera.refund.interfaces.vo;

import cc.ivera.refund.domain.model.RefundInfo;
import lombok.Data;

/**
 * 退款状态查询响应 VO。
 */
@Data
public class RefundStatusVO {

    private RefundInfo refundInfo;
}
