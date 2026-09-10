package cc.ivera.refund.interfaces.dto;

import lombok.Data;

/**
 * 管理员受理退款请求。
 * SHIPPED + REFUND_ONLY 必须指定 goodsDisposition：LOST / RECOVERED。
 */
@Data
public class RefundAcceptRequest {
    private String remark;
    private String goodsDisposition;
}
