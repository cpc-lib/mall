package cc.ivera.vo;

import cc.ivera.entity.RefundItem;
import cc.ivera.entity.RefundOrder;
import lombok.Data;
import java.util.List;

@Data
public class RefundApplyVO {
    private RefundOrder apply;
    private List<RefundItem> items;
    /** 渠道退款失败原因（status=FAILED 时从 RefundInfo.contentReturn 填充，其余为 null） */
    private String failReason;
}
