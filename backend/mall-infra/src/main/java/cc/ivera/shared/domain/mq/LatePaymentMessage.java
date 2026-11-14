package cc.ivera.shared.domain.mq;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 晚到支付事件：订单已关闭/取消后渠道才确认收款，需要原路冲正。
 */
@Data
public class LatePaymentMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String eventId;//事件唯一标识，取本次 paymentNo
    private String orderNo;
    private String paymentNo;
    private String channel;
    private Integer paidAmount;
    private Date paidTime;
}
