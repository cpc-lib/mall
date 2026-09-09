package cc.ivera.refund.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 渠道退款流水（t_refund_info）：V1 退款申请/渠道退款记录。
 * 退款单（RefundOrder，V2）与渠道侧退款通过 refund_no 关联。
 */
@Data
public class RefundInfo {

    private Long id;

    private String orderNo;//商品订单编号

    private String refundNo;//退款单编号

    private String refundId;//支付系统退款单号

    private Integer totalFee;//原订单金额(分)

    private Integer refund;//退款金额(分)

    private String reason;//退款原因

    private String approvalStatus;//审核状态

    private String approveRemark;//审核备注

    private Date approvedTime;//审核时间

    private String refundStatus;//退款单状态

    private String contentReturn;//申请退款返回参数

    private String contentNotify;//退款结果通知参数

    private Date createTime;

    private Date updateTime;
}
