package cc.ivera.order.application.impl;

import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.user.domain.model.ShippingAddress;
import cc.ivera.order.application.CheckoutService;
import cc.ivera.order.application.OrderCloseMessageService;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.order.domain.repository.OrderShipmentRepository;
import cc.ivera.order.interfaces.dto.CheckoutRequest;
import cc.ivera.order.interfaces.vo.OrderDetailVO;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ReserveLine;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.cart.application.CartService;
import cc.ivera.user.application.ShippingAddressService;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.constant.DatePatterns;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import cc.ivera.cart.interfaces.vo.CartItemVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    /** 本地订单未支付超时（分钟），与 MQ 延迟关单 TTL、定时兜底扫描共用同一配置。 */
    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    private final CartService cartService;
    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final OrderShipmentRepository orderShipmentRepository;
    private final PaymentConfigGateway paymentConfigLoader;
    private final OrderCloseMessageService orderCloseMessageService;
    private final InventoryService inventoryService;
    private final ShippingAddressService addressService;

    public CheckoutServiceImpl(CartService cartService, ProductRepository productRepository,
                               OrderRepository orderRepository, OrderShipmentRepository orderShipmentRepository,
                               PaymentConfigGateway paymentConfigLoader,
                               OrderCloseMessageService orderCloseMessageService, InventoryService inventoryService,
                               ShippingAddressService addressService) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.orderShipmentRepository = orderShipmentRepository;
        this.paymentConfigLoader = paymentConfigLoader;
        this.orderCloseMessageService = orderCloseMessageService;
        this.inventoryService = inventoryService;
        this.addressService = addressService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO createOrder(Long userId, CheckoutRequest request) {
        List<CartItemVO> selected = cartService.selected(userId);
        if (selected.isEmpty()) throw new BizException("请选择需要结算的购物车商品");
        String channelCode = channelCode(request.getPaymentType());
        PaymentAppConfig payConfig = request.getPaymentAppId() == null
                ? paymentConfigLoader.getRequiredDefaultAppConfigByChannelCode(channelCode)
                : paymentConfigLoader.getRequiredAppConfig(request.getPaymentAppId());
        if (!channelCode.equals(payConfig.getChannelCode())) throw new BizException("支付应用与支付方式不匹配");

        int total = 0;
        List<Product> products = new ArrayList<>();
        for (CartItemVO cart : selected) {
            Product p = productRepository.findById(cart.getProductId());
            if (p == null || !CommonStatus.ENABLED.getType().equals(p.getProductStatus())) throw new BizException("商品不可售，productId=" + cart.getProductId());
            if (p.getStock() == null || p.getStock() < cart.getQuantity()) throw new BizException("当前库存不足，productId=" + cart.getProductId());
            total = Math.addExact(total, Math.multiplyExact(p.getPrice(), cart.getQuantity()));
            products.add(p);
        }
        // 收货信息（模拟物流对接）：addressId 优先取地址簿，否则用手工输入，最后兜底模拟值。
        ShippingAddress addr = request.getAddressId() != null
                ? addressService.getById(userId, request.getAddressId())
                : null;
        String receiverName;
        String receiverPhone;
        String receiverAddress;
        if (addr != null) {
            receiverName = addr.getReceiverName();
            receiverPhone = addr.getReceiverPhone();
            receiverAddress = addr.getProvince() + addr.getCity() + addr.getDistrict() + addr.getDetail();
        } else {
            receiverName = trimOrDefault(request.getReceiverName(), "模拟收货人");
            receiverPhone = trimOrDefault(request.getReceiverPhone(), "13800000000");
            receiverAddress = trimOrDefault(request.getReceiverAddress(), "模拟地址-请勿发货");
        }
        Date expireTime = new Date(System.currentTimeMillis() + orderExpireMinutes * 60 * 1000);
        OrderInfo order = OrderInfo.createNew(
            products.size() == 1 ? products.get(0).getTitle() : "多商品订单(" + products.size() + "项)",
            OrderNoUtils.getOrderNo(),
            userId,
            products.get(0).getId(), //兼容旧字段，真实商品组成以 t_order_item 为准
            total,
            request.getPaymentType(),
            payConfig.getAppId(),
            channelCode,
            expireTime,
            receiverName,
            receiverPhone,
            receiverAddress);
        orderRepository.save(order);

        List<OrderItem> items = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            CartItemVO cart = selected.get(i);
            Product product = products.get(i);
            OrderItem item = OrderItem.createSnapshot(order.getId(), order.getOrderNo(),
                product.getId(), product.getTitle(), product.getPrice(), cart.getQuantity());
            orderRepository.saveItem(item);
            items.add(item);
        }
        // V2：下单同步原子预占库存（同事务），库存不足整体回滚，替代仅有的前置库存检查。
        List<ReserveLine> reserveLines = items.stream()
            .map(item -> new ReserveLine(item.getId(), item.getProductId(), item.getQuantity()))
            .collect(Collectors.toList());
        inventoryService.reserveForOrder(order.getOrderNo(), order.getExpireTime(), reserveLines);
        // 事务性发件箱：延迟关单消息与订单同事务落库本地消息表，提交后投递 MQ
        orderCloseMessageService.sendCloseOrderMessage(order.getOrderNo(), order.getPaymentType());
        afterCommit(() -> cartService.clearSelected(userId));
        return detail(order, items);
    }

    @Override
    public List<OrderDetailVO> listMyOrders(Long userId) {
        List<OrderDetailVO> list = new ArrayList<>();
        for (OrderInfo order : orderRepository.listByUserIdCreateTimeDesc(userId)) {
            list.add(detail(order, items(order.getOrderNo())));
        }
        fillShipmentStatus(list);
        return list;
    }

    @Override
    public List<OrderDetailVO> listAllOrders(String payStatus, String orderStatus, String fulfillmentStatus,
                                             String orderNo, String userId, String startTime, String endTime) {
        String orderNoLike = (orderNo != null && !orderNo.trim().isEmpty()) ? "%" + orderNo.trim() + "%" : null;
        Long userIdValue = null;
        if (userId != null && !userId.trim().isEmpty()) {
            try {
                userIdValue = Long.parseLong(userId.trim());
            } catch (NumberFormatException e) {
                throw new BizException("用户编号格式不正确：" + userId.trim());
            }
        }
        Date start = parseFilterTime(startTime, " 00:00:00", "开始时间");
        Date end = parseFilterTime(endTime, " 23:59:59", "结束时间");
        if (start != null && end != null && start.after(end)) throw new BizException("开始时间不能晚于结束时间");
        List<OrderDetailVO> list = new ArrayList<>();
        for (OrderInfo order : orderRepository.searchAdmin(
                payStatus, orderStatus, fulfillmentStatus, orderNoLike, userIdValue, start, end)) {
            list.add(detail(order, items(order.getOrderNo())));
        }
        fillShipmentStatus(list);
        return list;
    }

    /** 解析管理员订单筛选时间：接受 yyyy-MM-dd（按 dayPad 补时分秒）或 DatePatterns.DATETIME；空白返回 null。 */
    private static Date parseFilterTime(String value, String dayPad, String label) {
        if (value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        if (v.length() == 10) {
            v = v + dayPad;
        }
        SimpleDateFormat sdf = new SimpleDateFormat(DatePatterns.DATETIME);
        sdf.setLenient(false);
        try {
            return sdf.parse(v);
        } catch (ParseException e) {
            throw new BizException(label + "格式不正确，应为 yyyy-MM-dd 或 " + DatePatterns.DATETIME);
        }
    }

    @Override
    public OrderDetailVO getOrder(Long userId, String orderNo) {
        OrderInfo order = orderRepository.findByOrderNoAndUserId(orderNo, userId);
        if (order == null) throw new BizException("订单不存在或无权访问");
        OrderDetailVO vo = detail(order, items(orderNo));
        fillShipmentStatus(java.util.Collections.singletonList(vo));
        return vo;
    }

    @Override
    public void assertOwnership(Long userId, String orderNo) {
        OrderInfo order = orderRepository.findByOrderNoAndUserId(orderNo, userId);
        if (order == null) {
            throw new BizException("订单不存在或无权访问");
        }
    }

    private List<OrderItem> items(String orderNo) {
        return orderRepository.listItemsByOrderNo(orderNo);
    }

    /** 批量填充物流单最新状态（每单取 id 最大的运单），供前端按 DELIVERED 控制确认收货按钮。 */
    private void fillShipmentStatus(List<OrderDetailVO> list) {
        if (list == null || list.isEmpty()) return;
        List<String> orderNos = new ArrayList<>();
        for (OrderDetailVO vo : list) {
            if (vo.getOrder() != null) orderNos.add(vo.getOrder().getOrderNo());
        }
        if (orderNos.isEmpty()) return;
        List<OrderShipment> shipments = orderShipmentRepository.listByOrderNos(orderNos);
        Map<String, String> latest = new HashMap<>();
        for (OrderShipment s : shipments) {
            latest.putIfAbsent(s.getOrderNo(), s.getStatus());
        }
        for (OrderDetailVO vo : list) {
            vo.setShipmentStatus(latest.get(vo.getOrder().getOrderNo()));
        }
    }

    private OrderDetailVO detail(OrderInfo o, List<OrderItem> i) {
        // V1 兼容映射（spec 增量兼容）：老前端按 V1 中文状态值渲染与禁用按钮，
        // orderStatus 输出 V1 旧值；payStatus/fulfillmentStatus/refundStatus 等新字段原样透出。
        o.setOrderStatus(o.toLegacyViewStatus());
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrder(o);
        vo.setItems(i);
        return vo;
    }

    private String channelCode(String paymentType) {
        if (cc.ivera.payment.domain.enums.PayType.WXPAY.getType().equals(paymentType)) return PaymentConfigGateway.CHANNEL_WXPAY;
        if (cc.ivera.payment.domain.enums.PayType.ALIPAY.getType().equals(paymentType)) return PaymentConfigGateway.CHANNEL_ALIPAY;
        throw new BizException("不支持的支付方式：" + paymentType);
    }

    private String trimOrDefault(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    private void afterCommit(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            r.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCommit() {
                r.run();
            }
        });
    }
}
