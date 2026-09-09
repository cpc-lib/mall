package cc.ivera.payment.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 支付单 PO：t_payment_order（本地订单 1:N 渠道支付尝试）。
 */
@Data
@TableName("t_payment_order")
public class PaymentOrderPO extends BaseEntity {

    private String paymentNo;//商户支付单编号（每次发起支付生成）

    private String orderNo;//商户订单编号

    private String channel;//支付渠道：WXPAY、ALIPAY

    private String channelOrderNo;//渠道侧交易号（微信 transaction_id / 支付宝 trade_no）

    private String codeUrl;//支付二维码连接（NATIVE）

    private Integer requestAmount;//请求支付金额(分)

    private Integer paidAmount;//实际支付金额(分)

    private Integer refundFrozenAmount;//渠道退款冻结金额(分)

    private Integer refundedAmount;//渠道累计已退款金额(分)

    private String status;//支付单状态：CREATED/PAYING/SUCCESS/CLOSED

    private Date expireTime;//支付单过期时间（必须早于等于业务订单过期时间）

    private Date paidTime;//支付成功时间
}
