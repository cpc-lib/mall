package cc.ivera.payment.domain.model;

import lombok.Data;

/**
 * 支付流水（t_payment_info）：渠道支付通知/查单确认的支付日志。
 */
@Data
public class PaymentInfo {

    private Long id;

    private String orderNo;//商品订单编号

    private String transactionId;//支付系统交易编号

    private String paymentType;//支付类型

    private String tradeType;//交易类型

    private String tradeState;//交易状态

    private Integer payerTotal;//支付金额(分)

    private String content;//通知参数

    private java.util.Date createTime;

    private java.util.Date updateTime;
}
