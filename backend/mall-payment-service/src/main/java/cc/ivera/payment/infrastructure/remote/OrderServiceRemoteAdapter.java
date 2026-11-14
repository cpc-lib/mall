package cc.ivera.payment.infrastructure.remote;

import cc.ivera.api.order.OrderPaymentApi;
import cc.ivera.api.order.OrderStatusCommandApi;
import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.payment.infrastructure.remote.client.OrderServiceFeignClient;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import org.springframework.stereotype.Component;

/**
 * 订单远程适配器：payment-service 只通过 order 契约读取/建单/推进状态，
 * 不引用订单应用服务或仓储。
 */
@Component
public class OrderServiceRemoteAdapter implements OrderPaymentApi, OrderStatusCommandApi {
    private final OrderServiceFeignClient client;

    public OrderServiceRemoteAdapter(OrderServiceFeignClient client) {
        this.client = client;
    }

    @Override
    public OrderInfo getOrderByOrderNo(String orderNo) {
        return unwrap(client.getOrderByOrderNo(orderNo));
    }

    @Override
    public OrderInfo createOrReuseOrder(Long productId, String paymentType, Long paymentAppId, String paymentChannelCode) {
        return unwrap(client.createOrReuseOrder(
            new OrderServiceFeignClient.CreateOrReuseBody(productId, paymentType, paymentAppId, paymentChannelCode)));
    }

    @Override
    public void saveCodeUrl(String orderNo, String codeUrl) {
        unwrap(client.saveCodeUrl(orderNo, new OrderServiceFeignClient.CodeUrlBody(codeUrl)));
    }

    @Override
    public String getOrderStatus(String orderNo) {
        return unwrap(client.getOrderStatus(orderNo));
    }

    @Override
    public boolean markPaying(String orderNo) {
        return Boolean.TRUE.equals(unwrap(client.markPaying(new OrderServiceFeignClient.OrderNoBody(orderNo))));
    }

    @Override
    public boolean closeIfNotPay(String orderNo, OrderStatus target) {
        return Boolean.TRUE.equals(unwrap(client.closeIfNotPay(
            new OrderServiceFeignClient.CloseIfNotPayBody(orderNo, target))));
    }

    @Override
    public void closeOversold(String orderNo) {
        unwrap(client.closeOversold(new OrderServiceFeignClient.OrderNoBody(orderNo)));
    }

    private <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("订单服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "订单服务调用失败" : message);
    }
}
