package cc.ivera.service.impl;

import cc.ivera.config.PaymentAppConfig;
import cc.ivera.config.PaymentConfigLoader;
import cc.ivera.dto.checkout.CheckoutRequest;
import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderItem;
import cc.ivera.entity.OrderShipment;
import cc.ivera.entity.Product;
import cc.ivera.enums.FulfillmentStatus;
import cc.ivera.enums.OrderLifecycleStatus;
import cc.ivera.enums.OrderRefundStatus;
import cc.ivera.enums.OrderStatus;
import cc.ivera.enums.PayStatus;
import cc.ivera.enums.PayType;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.mapper.OrderItemMapper;
import cc.ivera.mapper.OrderShipmentMapper;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.service.CartService;
import cc.ivera.service.CheckoutService;
import cc.ivera.service.InventoryService;
import cc.ivera.service.OrderCloseMessageService;
import cc.ivera.util.OrderNoUtils;
import cc.ivera.vo.CartItemVO;
import cc.ivera.vo.OrderDetailVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

@Service
public class CheckoutServiceImpl implements CheckoutService {

    /** 本地订单未支付超时（分钟），与 MQ 延迟关单 TTL、定时兜底扫描共用同一配置。 */
    @Value("${payment.order.expire-minutes:3}")
    private long orderExpireMinutes;

    private final CartService cartService;
    private final ProductMapper productMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final PaymentConfigLoader paymentConfigLoader;
    private final OrderCloseMessageService orderCloseMessageService;
    private final InventoryService inventoryService;
    private final cc.ivera.service.ShippingAddressService addressService;
    private final OrderShipmentMapper orderShipmentMapper;

    public CheckoutServiceImpl(CartService cartService, ProductMapper productMapper, OrderInfoMapper orderInfoMapper,
                               OrderItemMapper orderItemMapper, PaymentConfigLoader paymentConfigLoader,
                               OrderCloseMessageService orderCloseMessageService, InventoryService inventoryService,
                               cc.ivera.service.ShippingAddressService addressService,
                               OrderShipmentMapper orderShipmentMapper) {
        this.cartService = cartService; this.productMapper = productMapper; this.orderInfoMapper = orderInfoMapper;
        this.orderItemMapper = orderItemMapper; this.paymentConfigLoader = paymentConfigLoader;
        this.orderCloseMessageService = orderCloseMessageService; this.inventoryService = inventoryService;
        this.addressService = addressService;
        this.orderShipmentMapper = orderShipmentMapper;
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
            Product p = productMapper.selectById(cart.getProductId());
            if (p == null || !"ENABLED".equals(p.getProductStatus())) throw new BizException("商品不可售，productId=" + cart.getProductId());
            if (p.getStock() == null || p.getStock() < cart.getQuantity()) throw new BizException("当前库存不足，productId=" + cart.getProductId());
            total = Math.addExact(total, Math.multiplyExact(p.getPrice(), cart.getQuantity()));
            products.add(p);
        }
        OrderInfo order = new OrderInfo();
        order.setOrderNo(OrderNoUtils.getOrderNo()); order.setUserId(userId);
        order.setProductId(products.get(0).getId()); //兼容旧字段，真实商品组成以 t_order_item 为准
        order.setTitle(products.size() == 1 ? products.get(0).getTitle() : "多商品订单(" + products.size() + "项)");
        order.setTotalFee(total); order.setLegacyStatus(OrderStatus.NOTPAY.getType()); order.setPaymentType(request.getPaymentType());
        order.setPaymentAppId(payConfig.getAppId()); order.setPaymentChannelCode(channelCode); order.setVersion(0);
        order.setExpireTime(new java.util.Date(System.currentTimeMillis() + orderExpireMinutes * 60 * 1000));
        // V2 四维状态显式落库（与 DDL 默认值一致），保证下单响应的 VO 不依赖 DB 回填。
        order.setOrderStatus(OrderLifecycleStatus.WAIT_PAY.getType());
        order.setPayStatus(PayStatus.UNPAID.getType());
        order.setFulfillmentStatus(FulfillmentStatus.WAIT_SHIP.getType());
        order.setRefundStatus(OrderRefundStatus.NONE.getType());
        // 收货信息（模拟物流对接）：addressId 优先取地址簿，否则用手工输入，最后兜底模拟值。
        cc.ivera.entity.ShippingAddress addr = request.getAddressId() != null
                ? addressService.getById(userId, request.getAddressId())
                : null;
        if (addr != null) {
            order.setReceiverName(addr.getReceiverName());
            order.setReceiverPhone(addr.getReceiverPhone());
            order.setReceiverAddress(addr.getProvince() + addr.getCity() + addr.getDistrict() + addr.getDetail());
        } else {
            order.setReceiverName(trimOrDefault(request.getReceiverName(), "模拟收货人"));
            order.setReceiverPhone(trimOrDefault(request.getReceiverPhone(), "13800000000"));
            order.setReceiverAddress(trimOrDefault(request.getReceiverAddress(), "模拟地址-请勿发货"));
        }
        orderInfoMapper.insert(order);

        List<OrderItem> items = new ArrayList<>();
        for (int i=0;i<selected.size();i++) {
            CartItemVO cart=selected.get(i); Product product=products.get(i);
            OrderItem item=new OrderItem(); item.setOrderId(order.getId()); item.setOrderNo(order.getOrderNo()); item.setProductId(product.getId());
            item.setProductTitle(product.getTitle()); item.setUnitPrice(product.getPrice()); item.setQuantity(cart.getQuantity()); item.setRefundedQty(0);
            // V2 成交快照金额（退款资金上限依据）：无优惠券，pay_amount=unit_price*quantity。
            item.setDealUnitAmount(product.getPrice());
            item.setOriginalTotalAmount(Math.multiplyExact(product.getPrice(), cart.getQuantity()));
            item.setDiscountAmount(0);
            item.setPayAmount(Math.multiplyExact(product.getPrice(), cart.getQuantity()));
            item.setRefundFrozenQty(0); item.setRefundFrozenAmount(0); item.setRefundedAmount(0); item.setRestockedQty(0);
            orderItemMapper.insert(item); items.add(item);
        }
        // V2：下单同步原子预占库存（同事务），库存不足整体回滚，替代仅有的前置库存检查。
        inventoryService.reserveForOrder(order, items);
        // 事务性发件箱：延迟关单消息与订单同事务落库本地消息表，提交后投递 MQ
        orderCloseMessageService.sendCloseOrderMessage(order.getOrderNo(), order.getPaymentType());
        afterCommit(() -> cartService.clearSelected(userId));
        return detail(order, items);
    }

    @Override public List<OrderDetailVO> listMyOrders(Long userId) {
        QueryWrapper<OrderInfo> q=new QueryWrapper<>(); q.eq("user_id",userId).orderByDesc("create_time");
        List<OrderDetailVO> list=new ArrayList<>(); for(OrderInfo order:orderInfoMapper.selectList(q)) list.add(detail(order, items(order.getOrderNo())));
        fillShipmentStatus(list); return list;
    }
    @Override public List<OrderDetailVO> listAllOrders(String payStatus, String orderStatus, String fulfillmentStatus,
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
        for (OrderInfo order : orderInfoMapper.selectAdminOrderList(
                payStatus, orderStatus, fulfillmentStatus, orderNoLike, userIdValue, start, end)) {
            list.add(detail(order, items(order.getOrderNo())));
        }
        fillShipmentStatus(list);
        return list;
    }
    /** 解析管理员订单筛选时间：接受 yyyy-MM-dd（按 dayPad 补时分秒）或 yyyy-MM-dd HH:mm:ss；空白返回 null。 */
    private static Date parseFilterTime(String value, String dayPad, String label) {
        if (value == null || value.trim().isEmpty()) return null;
        String v = value.trim();
        String pattern = "yyyy-MM-dd HH:mm:ss";
        if (v.length() == 10) { v = v + dayPad; }
        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        sdf.setLenient(false);
        try { return sdf.parse(v); }
        catch (ParseException e) { throw new BizException(label + "格式不正确，应为 yyyy-MM-dd 或 yyyy-MM-dd HH:mm:ss"); }
    }
    @Override public OrderDetailVO getOrder(Long userId,String orderNo) {
        QueryWrapper<OrderInfo> q=new QueryWrapper<>(); q.eq("order_no",orderNo).eq("user_id",userId); OrderInfo order=orderInfoMapper.selectOne(q);
        if(order==null) throw new BizException("订单不存在或无权访问");
        OrderDetailVO vo=detail(order,items(orderNo));
        fillShipmentStatus(java.util.Collections.singletonList(vo));
        return vo;
    }
    private List<OrderItem> items(String orderNo){QueryWrapper<OrderItem> q=new QueryWrapper<>(); q.eq("order_no",orderNo).orderByAsc("id"); return orderItemMapper.selectList(q);}

    /** 批量填充物流单最新状态（每单取 id 最大的运单），供前端按 DELIVERED 控制确认收货按钮。 */
    private void fillShipmentStatus(List<OrderDetailVO> list){
        if(list==null||list.isEmpty()) return;
        List<String> orderNos=new ArrayList<>();
        for(OrderDetailVO vo:list){ if(vo.getOrder()!=null) orderNos.add(vo.getOrder().getOrderNo()); }
        if(orderNos.isEmpty()) return;
        List<OrderShipment> shipments=orderShipmentMapper.selectList(new QueryWrapper<OrderShipment>()
                .in("order_no",orderNos).orderByDesc("id"));
        Map<String,String> latest=new HashMap<>();
        for(OrderShipment s:shipments){ latest.putIfAbsent(s.getOrderNo(),s.getStatus()); }
        for(OrderDetailVO vo:list){ vo.setShipmentStatus(latest.get(vo.getOrder().getOrderNo())); }
    }
    private OrderDetailVO detail(OrderInfo o,List<OrderItem> i){
        // V1 兼容映射（spec 增量兼容）：老前端按 V1 中文状态值渲染与禁用按钮，
        // orderStatus 输出 V1 旧值；payStatus/fulfillmentStatus/refundStatus 等新字段原样透出。
        o.setOrderStatus(legacyViewStatus(o));
        OrderDetailVO vo=new OrderDetailVO();vo.setOrder(o);vo.setItems(i);return vo;
    }

    /** refund_status 非 NONE 优先映射旧退款值，其次按交易生命周期映射。 */
    private String legacyViewStatus(OrderInfo order) {
        String refundStatus = order.getRefundStatus();
        if (OrderRefundStatus.REFUNDING.getType().equals(refundStatus)) return OrderStatus.REFUND_PROCESSING.getType();
        if (OrderRefundStatus.PARTIAL_REFUNDED.getType().equals(refundStatus)) return OrderStatus.PARTIAL_REFUND.getType();
        if (OrderRefundStatus.FULL_REFUNDED.getType().equals(refundStatus)) return OrderStatus.REFUND_SUCCESS.getType();
        String lifecycle = order.getOrderStatus();
        if (OrderLifecycleStatus.CLOSED.getType().equals(lifecycle)) return OrderStatus.CLOSED.getType();
        if (lifecycle == null || OrderLifecycleStatus.WAIT_PAY.getType().equals(lifecycle)) return OrderStatus.NOTPAY.getType();
        return OrderStatus.SUCCESS.getType();
    }
    private String channelCode(String paymentType){if(PayType.WXPAY.getType().equals(paymentType))return PaymentConfigLoader.CHANNEL_WXPAY;if(PayType.ALIPAY.getType().equals(paymentType))return PaymentConfigLoader.CHANNEL_ALIPAY;throw new BizException("不支持的支付方式："+paymentType);}
    private String trimOrDefault(String v, String def){return (v == null || v.trim().isEmpty()) ? def : v.trim();}
    private void afterCommit(Runnable r){if(!TransactionSynchronizationManager.isSynchronizationActive()){r.run();return;}TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter(){@Override public void afterCommit(){r.run();}});}
}
