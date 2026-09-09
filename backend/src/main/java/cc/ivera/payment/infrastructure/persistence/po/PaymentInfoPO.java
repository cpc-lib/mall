package cc.ivera.payment.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 支付流水 PO：t_payment_info。
 */
@Data
@TableName("t_payment_info")
public class PaymentInfoPO extends BaseEntity {

    private String orderNo;//商品订单编号

    private String transactionId;//支付系统交易编号

    private String paymentType;//支付类型

    private String tradeType;//交易类型

    private String tradeState;//交易状态

    private Integer payerTotal;//支付金额(分)

    private String content;//通知参数
}
