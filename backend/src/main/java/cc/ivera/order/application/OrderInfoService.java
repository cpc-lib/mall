package cc.ivera.order.application;

import cc.ivera.order.domain.enums.OrderStatus;
import cc.ivera.order.domain.model.OrderInfo;

import java.util.Date;
import java.util.List;

/**
 * 订单应用服务：下单（快速购买入口）、订单状态推进与查询。
 */
public interface OrderInfoService {

    OrderInfo createOrReuseOrder(Long productId, String paymentType);

    OrderInfo createOrReuseOrder(Long productId, String paymentType, Long paymentAppId, String paymentChannelCode);

    /**
     * 幂等保存二维码地址：只有 code_url 为空时才写入，避免并发请求互相覆盖。
     */
    void saveCodeUrl(String orderNo, String codeUrl);

    List<OrderInfo> listOrderByCreateTimeDesc();

    void updateStatusByOrderNo(String orderNo, OrderStatus orderStatus);

    boolean updateStatusByOrderNoIfStatus(String orderNo, OrderStatus currentStatus, OrderStatus targetStatus);

    String getOrderStatus(String orderNo);

    OrderInfo getOrderByOrderNo(String orderNo);

    /**
     * 待发货订单：已支付 + 待发货 + 无退款，按支付时间升序（管理员待发货列表）。
     */
    List<OrderInfo> listWaitShipOrders();

    /**
     * 超时未支付订单扫描：legacy NOTPAY 且创建时间早于 cutoff（定时兜底关单用）。
     */
    List<OrderInfo> listTimeoutNotPayOrders(Date cutoff);
}
