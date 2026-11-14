package cc.ivera.shared.domain.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 重复支付事件：同一业务订单已有一笔有效成交，本次另一笔支付需原路冲正。
 */
@Data
public class DuplicatePaymentMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;//事件唯一标识，取本次 paymentNo
    private String orderNo;
    private String paymentNo;
    private String channel;
    private Integer paidAmount;
    private Date paidTime;
}
