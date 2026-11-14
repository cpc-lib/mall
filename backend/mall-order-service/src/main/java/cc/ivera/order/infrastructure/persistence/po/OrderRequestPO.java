package cc.ivera.order.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 下单请求幂等 PO：t_order_request。
 */
@Data
@TableName("t_order_request")
public class OrderRequestPO extends BaseEntity {

    private String idemKey;

    private Long userId;

    private String api;

    private String requestHash;

    private String status;

    private String orderNo;

    private String responseBody;

    private Date expireAt;
}
