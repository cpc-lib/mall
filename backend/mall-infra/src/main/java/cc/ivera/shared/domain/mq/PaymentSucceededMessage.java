package cc.ivera.shared.domain.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 支付成交事件：一笔业务订单的有效成交事实（t_payment_deal 已落库）。
 */
@Data
public class PaymentSucceededMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;//事件唯一标识，取 paymentNo
    private String orderNo;
    private String paymentNo;
    private String channel;
    private Integer paidAmount;
    private Date paidTime;
}
