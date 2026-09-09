package cc.ivera.refund.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 退款单 PO：t_refund_order（资金退款主线 V2）。
 * 异常支付冲正（DUPLICATE_PAYMENT/LATE_PAYMENT）也落本表，但不占售后额度、不写退款明细。
 */
@Data
@TableName("t_refund_order")
public class RefundOrderPO extends BaseEntity {
    private String refundNo;
    private String orderNo;
    private Long userId;
    private String refundType;//退款类型：CANCEL_BEFORE_SHIP/RETURN_AND_REFUND/REFUND_ONLY/PRICE_ADJUSTMENT/DUPLICATE_PAYMENT/LATE_PAYMENT
    private String paymentNo;//退款来源支付单编号（原路退回依据）
    private Integer refundAmount;//退款金额(分)，服务端按订单快照计算
    private String reason;
    private String status;//退款单状态：APPLYING/APPROVED/REJECTED/CANCELLED/REFUNDING/SUCCESS/FAILED
    private String legacyApplyStatus;//V1 旧申请状态值（PENDING/ACCEPTED/...），仅供审计/回滚对照
    private String applyType;//申请来源：USER/ADMIN/SYSTEM（历史值 SYSTEM_OVERSOLD 保留审计）
    private String adminRemark;
    private Date acceptedTime;
    private Date successTime;//退款成功时间
}
