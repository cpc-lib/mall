package cc.ivera.order.application;

import cc.ivera.order.interfaces.dto.CheckoutRequest;
import cc.ivera.order.interfaces.vo.OrderDetailVO;

import java.util.List;

/**
 * 购物车结算/订单查询应用服务。
 */
public interface CheckoutService {
    OrderDetailVO createOrder(Long userId, CheckoutRequest request);

    List<OrderDetailVO> listMyOrders(Long userId);

    OrderDetailVO getOrder(Long userId, String orderNo);

    /**
     * 订单归属校验：订单不存在或不属于该用户抛 BizException（防水平越权）。
     */
    void assertOwnership(Long userId, String orderNo);

    /**
     * 管理员查看全部订单（可选状态/订单号/用户编号/下单时间范围筛选）
     */
    List<OrderDetailVO> listAllOrders(String payStatus, String orderStatus, String fulfillmentStatus,
                                      String orderNo, String userId, String startTime, String endTime);
}
