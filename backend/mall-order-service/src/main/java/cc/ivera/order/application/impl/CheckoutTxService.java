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
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ReserveLine;
import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.user.application.ShippingAddressService;
import cc.ivera.user.domain.model.ShippingAddress;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 下单本地事务：落单、库存预占、延迟关单消息与清空购物车补偿。
 * 远程预占仍在本地事务内执行，失败时整体回滚并通过 afterRollback 释放预占（AGENTS 5.1）。
 */
@Service
public class CheckoutTxService {

    private final CartService cartService;
    private final ProductQueryApi productRepository;
    private final OrderRepository orderRepository;
    private final PaymentConfigGateway paymentConfigLoader;
    private final OrderCloseMessageService orderCloseMessageService;
    private final InventoryService inventoryService;
    private final ShippingAddressService addressService;

    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    public CheckoutTxService(CartService cartService, ProductQueryApi productRepository,
                             OrderRepository orderRepository, PaymentConfigGateway paymentConfigLoader,
                             OrderCloseMessageService orderCloseMessageService, InventoryService inventoryService,
                             ShippingAddressService addressService) {
        this.cartService = cartService;
        this.productRepository = productRepository;
        this.orderRepository = orderRepository;
        this.paymentConfigLoader = paymentConfigLoader;
        this.orderCloseMessageService = orderCloseMessageService;
        this.inventoryService = inventoryService;
        this.addressService = addressService;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderDetailVO create(Long userId, CheckoutRequest request, String orderNo) {
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
            if (p == null || !CommonStatus.ENABLED.getType().equals(p.getProductStatus()))
                throw new BizException("商品不可售，productId=" + cart.getProductId());
            if (p.getStock() == null || p.getStock() < cart.getQuantity())
                throw new BizException("当前库存不足，productId=" + cart.getProductId());
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
            orderNo,
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
        // V2：下单同步预占库存（远程库存服务），库存不足整体回滚；
        // 预占成功后本地事务若回滚，afterCompletion 补偿释放，避免库存长期滞留锁定桶。
        List<ReserveLine> reserveLines = items.stream()
            .map(item -> new ReserveLine(item.getId(), item.getProductId(), item.getQuantity()))
            .collect(Collectors.toList());
        inventoryService.reserveForOrder(order.getOrderNo(), order.getExpireTime(), reserveLines);
        afterRollback(() -> inventoryService.releaseReservation(order.getOrderNo()));
        // 事务性发件箱：延迟关单消息与订单同事务落库本地消息表，提交后投递 MQ
        orderCloseMessageService.sendCloseOrderMessage(order.getOrderNo(), order.getPaymentType());
        afterCommit(() -> cartService.clearSelected(userId));
        return detail(order, items);
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

    /**
     * 本地事务未提交（回滚或异常）时执行补偿；无事务上下文时无事可回滚。
     */
    private void afterRollback(Runnable r) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) r.run();
            }
        });
    }
}
