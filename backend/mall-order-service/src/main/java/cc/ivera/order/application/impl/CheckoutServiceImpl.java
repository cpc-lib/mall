package cc.ivera.order.application.impl;

import cc.ivera.order.application.CheckoutService;
import cc.ivera.order.application.dto.CheckoutRequest;
import cc.ivera.order.application.idempotency.OrderIdempotencyService;
import cc.ivera.order.application.saga.CheckoutSagaService;
import cc.ivera.order.application.vo.OrderDetailVO;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.infrastructure.constant.DatePatterns;
import cc.ivera.shared.web.PageVO;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CheckoutServiceImpl implements CheckoutService {

    /**
     * 下单幂等维度：同一用户 + 该接口 + Idempotency-Key 只产生一个订单。
     */
    static final String API_CREATE_ORDER = "POST:/api/checkout/orders";

    private final OrderRepository orderRepository;
    private final OrderIdempotencyService orderIdempotencyService;
    private final CheckoutSagaService checkoutSagaService;

    public CheckoutServiceImpl(OrderRepository orderRepository,
                               OrderIdempotencyService orderIdempotencyService,
                               CheckoutSagaService checkoutSagaService) {
        this.orderRepository = orderRepository;
        this.orderIdempotencyService = orderIdempotencyService;
        this.checkoutSagaService = checkoutSagaService;
    }

    /**
     * 解析管理员订单筛选时间：接受 yyyy-MM-dd（按 dayPad 补时分秒）或 DatePatterns.DATETIME；空白返回 null。
     */
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
    public OrderDetailVO createOrder(Long userId, String idempotencyKey, CheckoutRequest request) {
        return orderIdempotencyService.execute(userId, API_CREATE_ORDER, idempotencyKey, request,
            () -> checkoutSagaService.checkout(userId, request));
    }

    @Override
    public PageVO<OrderDetailVO> listMyOrders(Long userId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = orderRepository.countByUserId(userId);
        List<OrderDetailVO> records = Collections.emptyList();
        if (total > 0) {
            records = toDetails(orderRepository.listByUserIdPage(userId, safeSize, (long) (safePage - 1) * safeSize));
        }
        return page(total, safePage, safeSize, records);
    }

    @Override
    public PageVO<OrderDetailVO> listAllOrders(String payStatus, String orderStatus, String fulfillmentStatus,
                                               String orderNo, String userId, String startTime, String endTime,
                                               int page, int size) {
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
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        long total = orderRepository.countAdminOrders(
            payStatus, orderStatus, fulfillmentStatus, orderNoLike, userIdValue, start, end);
        List<OrderDetailVO> records = Collections.emptyList();
        if (total > 0) {
            records = toDetails(orderRepository.searchAdmin(
                payStatus, orderStatus, fulfillmentStatus, orderNoLike, userIdValue, start, end,
                safeSize, (long) (safePage - 1) * safeSize));
        }
        return page(total, safePage, safeSize, records);
    }

    @Override
    public OrderDetailVO getOrder(Long userId, String orderNo) {
        OrderInfo order = orderRepository.findByOrderNoAndUserId(orderNo, userId);
        if (order == null) throw new BizException("订单不存在或无权访问");
        return detail(order, items(orderNo));
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

    /**
     * 整页订单一次批量取明细后按订单号分组，避免逐单查询（N+1）。
     */
    private List<OrderDetailVO> toDetails(List<OrderInfo> orders) {
        if (orders.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> orderNos = orders.stream().map(OrderInfo::getOrderNo).collect(Collectors.toList());
        Map<String, List<OrderItem>> itemsByOrder = orderRepository.listItemsByOrderNos(orderNos).stream()
            .collect(Collectors.groupingBy(OrderItem::getOrderNo));
        List<OrderDetailVO> list = new ArrayList<>(orders.size());
        for (OrderInfo order : orders) {
            list.add(detail(order, itemsByOrder.getOrDefault(order.getOrderNo(), Collections.emptyList())));
        }
        return list;
    }

    private PageVO<OrderDetailVO> page(long total, int page, int size, List<OrderDetailVO> records) {
        PageVO<OrderDetailVO> vo = new PageVO<>();
        vo.setTotal(total);
        vo.setPage(page);
        vo.setSize(size);
        vo.setRecords(records);
        return vo;
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
}
