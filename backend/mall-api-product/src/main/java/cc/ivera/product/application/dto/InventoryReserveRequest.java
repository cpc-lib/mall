package cc.ivera.product.application.dto;

import cc.ivera.product.domain.model.ReserveLine;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 内部预占请求：订单服务拆分后经此契约同步预占库存。
 */
@Data
public class InventoryReserveRequest {

    private String orderNo;

    private Date expireTime;

    private List<ReserveLine> lines;
}
