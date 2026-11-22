package cc.ivera.order.application;

import cc.ivera.order.application.dto.CheckoutRequest;
import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.shared.web.PageVO;

/**
 * 购物车结算/订单查询应用服务。
 */
public interface CheckoutService {
    /**
     * 下单入口：必须携带幂等键，同键同请求返回首次结果，失败请求可重试。
     */
    OrderDetailVO createOrder(Long userId, String idempotencyKey, CheckoutRequest request);

    /**
     * 用户订单分页（创建时间倒序）。
     */
    PageVO<OrderDetailVO> listMyOrders(Long userId, int page, int size);

    OrderDetailVO getOrder(Long userId, String orderNo);

    /**
     * 订单归属校验：订单不存在或不属于该用户抛 BizException（防水平越权）。
     */
    void assertOwnership(Long userId, String orderNo);

    /**
     * 管理员查看全部订单（可选状态/订单号/用户编号/下单时间范围筛选，创建时间倒序分页）
     */
    PageVO<OrderDetailVO> listAllOrders(String payStatus, String orderStatus, String fulfillmentStatus,
                                        String orderNo, String userId, String startTime, String endTime,
                                        int page, int size);
}
