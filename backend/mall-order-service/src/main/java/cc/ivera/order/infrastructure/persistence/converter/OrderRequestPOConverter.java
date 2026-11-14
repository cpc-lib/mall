package cc.ivera.order.infrastructure.persistence.converter;

import cc.ivera.order.domain.model.OrderRequest;
import cc.ivera.order.infrastructure.persistence.po.OrderRequestPO;

/**
 * 下单请求幂等 PO 与领域模型转换。
 */
public final class OrderRequestPOConverter {

    private OrderRequestPOConverter() {
    }

    public static OrderRequest toDomain(OrderRequestPO po) {
        if (po == null) return null;
        OrderRequest request = new OrderRequest();
        request.setId(po.getId());
        request.setIdemKey(po.getIdemKey());
        request.setUserId(po.getUserId());
        request.setApi(po.getApi());
        request.setRequestHash(po.getRequestHash());
        request.setStatus(po.getStatus());
        request.setOrderNo(po.getOrderNo());
        request.setResponseBody(po.getResponseBody());
        request.setExpireAt(po.getExpireAt());
        request.setCreateTime(po.getCreateTime());
        request.setUpdateTime(po.getUpdateTime());
        return request;
    }

    public static OrderRequestPO toPO(OrderRequest request) {
        OrderRequestPO po = new OrderRequestPO();
        po.setId(request.getId());
        po.setIdemKey(request.getIdemKey());
        po.setUserId(request.getUserId());
        po.setApi(request.getApi());
        po.setRequestHash(request.getRequestHash());
        po.setStatus(request.getStatus());
        po.setOrderNo(request.getOrderNo());
        po.setResponseBody(request.getResponseBody());
        po.setExpireAt(request.getExpireAt());
        return po;
    }
}
