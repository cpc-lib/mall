package cc.ivera.payment.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 有效支付成交事实（t_payment_deal）：一个业务订单只允许一笔有效成交。
 * 唯一键 order_no 是重复支付/晚到支付判定的最终兜底。
 */
@Data
public class PaymentDeal {

    private Long id;
    private String paymentNo;//本次有效成交的支付单号
    private String orderNo;//业务订单号，唯一
    private String channel;//支付渠道
    private Integer paidAmount;//成交金额（分）
    private Date paidTime;//渠道支付成功时间
    private Date createTime;
    private Date updateTime;
}
