package cc.ivera.service;

import cc.ivera.dto.checkout.CheckoutRequest;
import cc.ivera.vo.OrderDetailVO;

import java.util.List;

public interface CheckoutService {
    OrderDetailVO createOrder(Long userId, CheckoutRequest request);
    List<OrderDetailVO> listMyOrders(Long userId);
    OrderDetailVO getOrder(Long userId, String orderNo);
    /** 管理员查看全部订单（可选状态/订单号/用户编号/下单时间范围筛选） */
    List<OrderDetailVO> listAllOrders(String payStatus, String orderStatus, String fulfillmentStatus,
                                      String orderNo, String userId, String startTime, String endTime);
}
