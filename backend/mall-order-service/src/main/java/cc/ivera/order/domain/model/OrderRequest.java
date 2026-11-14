package cc.ivera.order.domain.model;

import cc.ivera.order.domain.enums.OrderRequestStatus;
import lombok.Data;

import java.util.Date;

/**
 * 下单请求幂等记录（t_order_request）：同一用户 + 接口 + Idempotency-Key 只产生一个订单。
 */
@Data
public class OrderRequest {

    private Long id;

    private String idemKey;

    private Long userId;

    private String api;

    private String requestHash;

    private String status;

    private String orderNo;

    private String responseBody;

    private Date expireAt;

    private Date createTime;

    private Date updateTime;

    public static OrderRequest processing(String idemKey, Long userId, String api, String requestHash, Date expireAt) {
        OrderRequest request = new OrderRequest();
        request.setIdemKey(idemKey);
        request.setUserId(userId);
        request.setApi(api);
        request.setRequestHash(requestHash);
        request.setStatus(OrderRequestStatus.PROCESSING.getType());
        request.setExpireAt(expireAt);
        return request;
    }
}
