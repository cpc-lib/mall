package cc.ivera.order.application.impl;

import cc.ivera.api.product.ProductQueryApi;
import cc.ivera.cart.application.CartService;
import cc.ivera.cart.application.dto.CartItemVO;
import cc.ivera.order.application.OrderCloseMessageService;
import cc.ivera.order.application.dto.CheckoutRequest;
import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentAppConfig;
import cc.ivera.product.domain.model.Product;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.infrastructure.mq.LocalMessage;
import cc.ivera.user.application.ShippingAddressService;
import cc.ivera.user.domain.model.ShippingAddress;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 下单本地事务：商品校验、落单、明细与延迟关单消息（Outbox）。
 * 库存预占与清空购物车已拆为 Saga 步骤，由 CheckoutSagaAction 在本地事务之外调用；
 * 本类只负责单步本地事务，补偿由状态机驱动。
 */
@Service
public class CheckoutTxService {

    private final CartService cartService;
    private final ProductQueryApi productRepository;
    private final OrderRepository orderRepository;
    private final PaymentConfigGateway paymentConfigLoader;
    private final OrderCloseMessageService orderCloseMessageService;
    private final ShippingAddressService addressService;
    private final LocalMessageService localMessageService;
    private final TransactionTemplate transactionTemplate;
    private final MeterRegistry meterRegistry;

    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    public CheckoutTxService(CartService cartService, ProductQueryApi productRepository,
                             OrderRepository orderRepository, PaymentConfigGateway paymentConfigLoader,
                             OrderCloseMessageService orderCloseMessageService,
                             ShippingAddressService addressService,
                             LocalMessageService localMessageService,
                             TransactionTemplate transactionTemplate,
                             MeterRegistry meterRegistry) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentConfigLoader = paymentConfigLoader;
        this.orderCloseMessageService = orderCloseMessageService;
        this.addressService = addressService;
        this.localMessageService = localMessageService;
        this.transactionTemplate = transactionTemplate;
        this.meterRegistry = meterRegistry;
    }

    /**
     * 构建结算上下文（只读，不写任何表）：作为 Saga 入参，包含预占与落单所需的商品快照、收货信息与支付配置。
     * 商品可售与库存前置校验在此完成；库存的权威闸门仍是商品服务 reserveStock 条件 UPDATE。
     */
    public Map<String, Object> buildCheckoutContext(Long userId, CheckoutRequest request, String orderNo) {
        OrderInfo existing = orderRepository.findByOrderNo(orderNo);
        if (existing != null && Objects.equals(existing.getUserId(), userId)) {
            return context(existing, orderRepository.listItemsByOrderNo(orderNo));
        }
        List<CartItemVO> selected = cartService.selected(userId);
        if (selected.isEmpty()) throw new BizException("请选择需要结算的购物车商品");
        String channelCode = channelCode(request.getPaymentType());
        PaymentAppConfig payConfig = request.getPaymentAppId() == null
            ? paymentConfigLoader.getRequiredDefaultAppConfigByChannelCode(channelCode)
            : paymentConfigLoader.getRequiredAppConfig(request.getPaymentAppId());
        if (!channelCode.equals(payConfig.getChannelCode())) throw new BizException("支付应用与支付方式不匹配");

        List<Long> productIds = selected.stream().map(CartItemVO::getProductId).toList();
        Map<Long, Product> productById = productRepository.findByIds(productIds).stream()
            .collect(Collectors.toMap(Product::getId, product -> product));
        int total = 0;
        List<Map<String, Object>> items = new ArrayList<>();
        for (CartItemVO cart : selected) {
            Product p = productById.get(cart.getProductId());
            if (p == null || !CommonStatus.ENABLED.getType().equals(p.getProductStatus()))
                throw new BizException("商品不可售，productId=" + cart.getProductId());
            if (p.getStock() == null || p.getStock() < cart.getQuantity())
                throw new BizException("当前库存不足，productId=" + cart.getProductId());
            total = Math.addExact(total, Math.multiplyExact(p.getPrice(), cart.getQuantity()));
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("productId", p.getId());
            line.put("title", p.getTitle());
            line.put("price", p.getPrice());
            line.put("quantity", cart.getQuantity());
            items.add(line);
        }
        // 收货信息（模拟物流对接）：addressId 优先取地址簿，否则用手工输入，最后兜底模拟值。
        ShippingAddress addr = request.getAddressId() != null
            ? addressService.getById(userId, request.getAddressId())
            : null;
        Date expireTime = new Date(System.currentTimeMillis() + orderExpireMinutes * 60 * 1000);

        Map<String, Object> checkout = new LinkedHashMap<>();
        checkout.put("orderNo", orderNo);
        checkout.put("userId", userId);
        checkout.put("paymentType", request.getPaymentType());
        checkout.put("paymentAppId", payConfig.getAppId());
        checkout.put("channelCode", channelCode);
        checkout.put("expireTime", expireTime.getTime());
        checkout.put("title", items.size() == 1
            ? (String) items.get(0).get("title")
            : "多商品订单(" + items.size() + "项)");
        checkout.put("firstProductId", items.get(0).get("productId"));
        checkout.put("total", total);
        checkout.put("receiverName", addr != null ? addr.getReceiverName()
            : trimOrDefault(request.getReceiverName(), "模拟收货人"));
        checkout.put("receiverPhone", addr != null ? addr.getReceiverPhone()
            : trimOrDefault(request.getReceiverPhone(), "13800000000"));
        checkout.put("receiverAddress", addr != null
            ? addr.getProvince() + addr.getCity() + addr.getDistrict() + addr.getDetail()
            : trimOrDefault(request.getReceiverAddress(), "模拟地址-请勿发货"));
        checkout.put("items", items);
        return checkout;
    }

    /**
     * 落单本地事务：按结算上下文的商品快照写订单、明细与延迟关单 Outbox。
     * 同一 orderNo 重复执行时直接返回既有订单上下文，保证状态机重试幂等。
     */
    public Map<String, Object> createOrder(Map<String, Object> checkout) {
        String orderNo = asString(checkout.get("orderNo"));
        Long userId = asLong(checkout.get("userId"));
        OrderInfo existing = orderRepository.findByOrderNo(orderNo);
        if (existing != null && Objects.equals(existing.getUserId(), userId)) {
            return context(existing, orderRepository.listItemsByOrderNo(orderNo));
        }
        OrderInfo order = OrderInfo.createNew(
            asString(checkout.get("title")),
            orderNo,
            userId,
            asLong(checkout.get("firstProductId")),
            asInt(checkout.get("total")),
            asString(checkout.get("paymentType")),
            asLong(checkout.get("paymentAppId")),
            asString(checkout.get("channelCode")),
            new Date(asLong(checkout.get("expireTime"))),
            asString(checkout.get("receiverName")),
            asString(checkout.get("receiverPhone")),
            asString(checkout.get("receiverAddress")));

        try {
            Map<String, Object> created = transactionTemplate.execute(status -> {
                OrderInfo persisted = orderRepository.findByOrderNo(orderNo);
                if (persisted != null && Objects.equals(persisted.getUserId(), userId)) {
                    return context(persisted, orderRepository.listItemsByOrderNo(orderNo));
                }
                orderRepository.save(order);
                List<OrderItem> items = new ArrayList<>();
                for (Object raw : asList(checkout.get("items"))) {
                    Map<?, ?> line = (Map<?, ?>) raw;
                    OrderItem item = OrderItem.createSnapshot(order.getId(), orderNo,
                        asLong(line.get("productId")), asString(line.get("title")),
                        asInt(line.get("price")), asInt(line.get("quantity")));
                    orderRepository.saveItem(item);
                    items.add(item);
                }
                // 事务性发件箱：延迟关单消息与订单同事务落库本地消息表，提交后投递 MQ
                orderCloseMessageService.sendCloseOrderMessage(order.getOrderNo(), order.getPaymentType());
                return context(order, items);
            });
            recordCheckout("success");
            return created;
        } catch (RuntimeException e) {
            recordCheckout("failure");
            throw e;
        }
    }

    private void recordCheckout(String result) {
        Counter.builder("mall.order.checkout").tag("result", result).register(meterRegistry).increment();
    }

    /**
     * 状态机完成后回读订单详情（只读）。
     */
    @Transactional(readOnly = true)
    public OrderDetailVO loadDetail(String orderNo) {
        OrderInfo order = orderRepository.findByOrderNo(orderNo);
        if (order == null) throw new BizException("订单不存在");
        return detail(order, orderRepository.listItemsByOrderNo(orderNo));
    }

    /**
     * 下单补偿：仅未支付订单可关闭，已支付/已关闭订单不受影响（条件 UPDATE，幂等）。
     * 关单成功后同步作废本地发件箱中的延迟关单消息，避免 30 分钟后仍投递并触发无意义的渠道查单。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelIfUnpaid(String orderNo) {
        int changed = orderRepository.cancelIfUnpaid(orderNo);
        if (changed > 0) {
            localMessageService.markConsumed(LocalMessage.BIZ_TYPE_ORDER_CLOSE, orderNo);
        }
    }

    private Map<String, Object> context(OrderInfo order, List<OrderItem> items) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("orderNo", order.getOrderNo());
        context.put("userId", order.getUserId());
        context.put("expireTime", order.getExpireTime() == null ? 0L : order.getExpireTime().getTime());
        List<Map<String, Object>> lines = new ArrayList<>();
        for (OrderItem item : items) {
            Map<String, Object> line = new LinkedHashMap<>();
            line.put("orderItemId", item.getId());
            line.put("productId", item.getProductId());
            line.put("quantity", item.getQuantity());
            lines.add(line);
        }
        context.put("items", lines);
        return context;
    }

    private OrderDetailVO detail(OrderInfo o, List<OrderItem> i) {
        o.setOrderStatus(o.toLegacyViewStatus());
        OrderDetailVO vo = new OrderDetailVO();
        vo.setOrder(o);
        vo.setItems(i);
        return vo;
    }

    private String channelCode(String paymentType) {
        if (PayType.WXPAY.getType().equals(paymentType)) return PaymentConfigGateway.CHANNEL_WXPAY;
        if (PayType.ALIPAY.getType().equals(paymentType)) return PaymentConfigGateway.CHANNEL_ALIPAY;
        throw new BizException("不支持的支付方式：" + paymentType);
    }

    private String trimOrDefault(String v, String def) {
        return (v == null || v.trim().isEmpty()) ? def : v.trim();
    }

    private static List<?> asList(Object value) {
        if (value instanceof List<?>) {
            return (List<?>) value;
        }
        throw new BizException("下单上下文缺少商品明细");
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Long asLong(Object value) {
        if (value == null) {
            return null;
        }
        return value instanceof Number ? ((Number) value).longValue() : Long.valueOf(String.valueOf(value));
    }

    private static int asInt(Object value) {
        if (value == null) {
            throw new BizException("下单上下文缺少数值");
        }
        return value instanceof Number ? ((Number) value).intValue() : Integer.parseInt(String.valueOf(value));
    }
}
