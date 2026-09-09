package cc.ivera.refund.interfaces.vo;

import cc.ivera.refund.domain.model.RefundInfo;
import lombok.Data;

import java.util.List;

/**
 * 退款申请单列表响应 VO。
 */
@Data
public class RefundListVO {

    private List<RefundInfo> list;
}
