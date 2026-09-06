package cc.ivera.mq;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderCloseMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    private String orderNo;

    private String paymentType;
}
