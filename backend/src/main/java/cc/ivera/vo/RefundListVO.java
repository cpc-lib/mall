package cc.ivera.vo;

import cc.ivera.entity.RefundInfo;
import lombok.Data;

import java.util.List;

/**
 * 退款申请单列表响应 VO。
 */
@Data
public class RefundListVO {

    private List<RefundInfo> list;
}
