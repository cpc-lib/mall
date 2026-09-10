package cc.ivera.refund.interfaces.vo;

import cc.ivera.refund.domain.model.RefundItem;
import cc.ivera.refund.domain.model.RefundOrder;
import lombok.Data;

import java.util.List;

@Data
public class RefundApplyVO {
    private RefundOrder apply;
    private List<RefundItem> items;
    private String fulfillmentStatus;
    private boolean goodsDispositionRequired;
    /**
     * 渠道退款失败原因（status=FAILED 时从 RefundInfo.contentReturn 填充，其余为 null）
     */
    private String failReason;
}
