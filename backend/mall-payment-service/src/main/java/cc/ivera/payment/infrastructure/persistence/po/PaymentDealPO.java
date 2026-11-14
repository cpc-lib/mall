package cc.ivera.payment.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 有效支付成交事实 PO：t_payment_deal。
 */
@Data
@TableName("t_payment_deal")
public class PaymentDealPO extends BaseEntity {

    private String paymentNo;//本次有效成交的支付单号
    private String orderNo;//业务订单号，唯一
    private String channel;//支付渠道
    private Integer paidAmount;//成交金额（分）
    private Date paidTime;//渠道支付成功时间
}
