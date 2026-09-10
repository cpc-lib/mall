package cc.ivera.refund.application.impl;

import cc.ivera.order.domain.enums.FulfillmentStatus;
import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.repository.OrderRepository;
import cc.ivera.payment.application.AliPayService;
import cc.ivera.payment.application.wxpay.WxPayRefundFacade;
import cc.ivera.payment.domain.enums.PayType;
import cc.ivera.payment.domain.gateway.PaymentConfigGateway;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.product.application.InventoryService;
import cc.ivera.product.domain.model.RefundStockLine;
import cc.ivera.refund.application.RefundApplicationService;
import cc.ivera.refund.application.RefundInfoService;
import cc.ivera.refund.application.RefundOrderService;
import cc.ivera.refund.domain.enums.RefundApprovalStatus;
import cc.ivera.refund.domain.enums.RefundGoodsDisposition;
import cc.ivera.refund.domain.enums.RefundOrderStatus;
import cc.ivera.refund.domain.enums.RefundStatus;
import cc.ivera.refund.domain.enums.RefundType;
import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.model.RefundItem;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.domain.policy.RefundPolicy;
import cc.ivera.refund.domain.repository.RefundInfoRepository;
import cc.ivera.refund.domain.repository.RefundItemRepository;
import cc.ivera.refund.domain.repository.RefundOrderRepository;
import cc.ivera.refund.interfaces.dto.RefundApplyItemRequest;
import cc.ivera.refund.interfaces.dto.RefundApplyRequest;
import cc.ivera.refund.interfaces.dto.RefundApplyUpdateRequest;
import cc.ivera.refund.interfaces.vo.RefundApplyVO;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.lock.DistributedLockTemplate;
import cc.ivera.shared.infrastructure.util.OrderNoUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 退款单域服务实现（V2）。
 * 核心规则：
 * - 三层冻结防线：申请创建冻结 Order+OrderItem（原子 UPDATE，任一不足整体失败）；渠道发起时冻结 PaymentOrder；
 * - 金额服务端计算（RefundPolicy 尾差规则），不信任前端金额；
 * - 退款类型与履约状态硬校验（决策 14）：CANCEL_BEFORE_SHIP 仅 WAIT_SHIP；RETURN_AND_REFUND 仅 RECEIVED；REFUND_ONLY 需 SHIPPED/RECEIVED；
 * - 库存处置：CANCEL_BEFORE_SHIP 受理即补；RETURN_AND_REFUND 签收质检后补；SHIPPED+REFUND_ONLY 由管理员判定 LOST/RECOVERED；
 * - 结转幂等：渠道成功 → 冻结转已退（DB 原子 SQL 守卫天然幂等）。
 */
@Service
@Slf4j
public class RefundOrderServiceImpl implements RefundOrderService {

    private static final String LEGACY_PENDING = "PENDING", LEGACY_ACCEPTED = "ACCEPTED", LEGACY_REJECTED = "REJECTED",
        LEGACY_CANCELLED = "CANCELLED", LEGACY_SUCCESS = "SUCCESS", LEGACY_FAILED = "FAILED";
    private static final Map<String, String> V1_TO_V2_STATUS = Collections.unmodifiableMap(new HashMap<String, String>() {{
        put(RefundStatus.SUCCESS.getType(), RefundOrderStatus.SUCCESS.getType());
        put(RefundStatus.FAILED.getType(), RefundOrderStatus.FAILED.getType());
        put(RefundStatus.ABNORMAL.getType(), RefundOrderStatus.FAILED.getType());
    }});
    private final RefundOrderRepository refundOrderRepository;
    private final RefundItemRepository refundItemRepository;
    private final RefundInfoRepository refundInfoRepository;
    private final OrderRepository orderRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final InventoryService inventoryService;
    private final DistributedLockTemplate lockTemplate;
    private final TransactionTemplate tx;
    private final AliPayService aliPayService;
    private final WxPayRefundFacade wxPayRefundFacade;
    private final RefundApplicationService refundApplicationService;
    private final RefundInfoService refundInfoService;

    // ==================== 用户端：申请 / 编辑 / 撤回 / 未发货取消 ====================

    public RefundOrderServiceImpl(RefundOrderRepository refundOrderRepository,
                                  RefundItemRepository refundItemRepository,
                                  RefundInfoRepository refundInfoRepository,
                                  OrderRepository orderRepository,
                                  PaymentOrderRepository paymentOrderRepository,
                                  InventoryService inventoryService,
                                  DistributedLockTemplate lockTemplate,
                                  TransactionTemplate tx,
                                  AliPayService aliPayService,
                                  WxPayRefundFacade wxPayRefundFacade,
                                  RefundApplicationService refundApplicationService,
                                  RefundInfoService refundInfoService) {
        this.refundOrderRepository = refundOrderRepository;
        this.refundItemRepository = refundItemRepository;
        this.refundInfoRepository = refundInfoRepository;
        this.orderRepository = orderRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.inventoryService = inventoryService;
        this.lockTemplate = lockTemplate;
        this.tx = tx;
        this.aliPayService = aliPayService;
        this.wxPayRefundFacade = wxPayRefundFacade;
        this.refundApplicationService = refundApplicationService;
        this.refundInfoService = refundInfoService;
    }

    @Override
    public RefundApplyVO create(Long userId, RefundApplyRequest request) {
        RefundInfo[] channelHolder = new RefundInfo[1];
        RefundApplyVO vo = lockTemplate.execute("refund:create:" + request.getOrderNo(), 5000L, -1L, () ->
            tx.execute(s -> doCreate(userId, request, null, channelHolder)));
        if (channelHolder[0] != null) {
            executeChannelRefund(orderNo(channelHolder[0].getOrderNo()), channelHolder[0]);
        }
        return vo;
    }

    @Override
    public RefundApplyVO update(Long userId, String refundNo, RefundApplyUpdateRequest request) {
        return lockTemplate.execute("refund:edit:" + refundNo, 5000L, -1L, () ->
            tx.execute(s -> doUpdate(userId, refundNo, request)));
    }

    @Override
    public void cancel(Long userId, String refundNo) {
        lockTemplate.execute("refund:cancel:" + refundNo, 5000L, -1L, () -> {
            tx.execute(s -> {
                doCancelOrReject(userId, refundNo, null, true);
                return null;
            });
            return null;
        });
    }

    // ==================== 管理端：拒绝 / 受理 / 签收 / 重试 / 差价退款 ====================

    @Override
    public RefundApplyVO cancelPaidOrder(Long userId, String orderNo) {
        RefundInfo[] channelHolder = new RefundInfo[1];
        RefundApplyVO vo = lockTemplate.execute("refund:create:" + orderNo, 5000L, -1L, () ->
            tx.execute(s -> doCancelPaidOrder(userId, orderNo, channelHolder)));
        if (channelHolder[0] != null) {
            executeChannelRefund(orderNo(channelHolder[0].getOrderNo()), channelHolder[0]);
        }
        return vo;
    }

    @Override
    public void reject(String refundNo, String remark) {
        lockTemplate.execute("refund:cancel:" + refundNo, 5000L, -1L, () -> {
            tx.execute(s -> {
                doCancelOrReject(null, refundNo, remark, false);
                return null;
            });
            return null;
        });
    }

    @Override
    public void accept(String refundNo, String remark, String goodsDisposition) {
        executeWithLockAndChannelRefund("refund:accept:" + refundNo,
            () -> prepareAccept(refundNo, remark, goodsDisposition));
    }

    @Override
    public void confirmReturn(String refundNo, String remark) {
        executeWithLockAndChannelRefund("refund:confirm-return:" + refundNo, () -> prepareConfirmReturn(refundNo, remark));
    }

    @Override
    public void retry(String refundNo) {
        executeWithLockAndChannelRefund("refund:retry:" + refundNo, () -> prepareRetry(refundNo));
    }

    private void executeWithLockAndChannelRefund(String lockKey, java.util.function.Supplier<RefundInfo> prepare) {
        lockTemplate.execute(lockKey, 5000L, -1L, () -> {
            RefundInfo channelRefund = tx.execute(s -> prepare.get());
            if (channelRefund != null) {
                executeChannelRefund(orderNo(channelRefund.getOrderNo()), channelRefund);
            }
            return null;
        });
    }

    // ==================== 结转（渠道退款成功，幂等） ====================

    @Override
    public RefundApplyVO createPriceAdjustment(String orderNo, Integer amount, String reason) {
        return lockTemplate.execute("refund:create:" + orderNo, 5000L, -1L, () ->
            tx.execute(s -> doCreatePriceAdjustment(orderNo, amount, reason)));
    }

    // ==================== 查询 ====================

    @Override
    public void settle(String refundNo) {
        lockTemplate.execute("refund:settle:" + refundNo, 5000L, -1L, () -> {
            tx.execute(s -> {
                doSettle(refundNo);
                return null;
            });
            return null;
        });
    }

    @Override
    public List<RefundApplyVO> listForUser(Long userId) {
        return details(refundOrderRepository.listByUserIdCreateTimeDesc(userId));
    }

    // ==================== 内部：创建链路 ====================

    @Override
    public List<RefundApplyVO> listAll() {
        return details(refundOrderRepository.listAllCreateTimeDesc());
    }

    private RefundApplyVO doCreate(Long userId, RefundApplyRequest request, String forcedType, RefundInfo[] channelHolder) {
        OrderInfo order = orderRepository.findByOrderNo(request.getOrderNo());
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException("订单不存在或无权访问");
        }
        requirePaidActive(order);
        String refundType = forcedType != null ? forcedType : resolveRefundType(request.getRefundType(), order.getFulfillmentStatus());
        checkFulfillment(refundType, order.getFulfillmentStatus());
        // 仅退款一键整单退：前端可不传明细，服务端按整单剩余可退数量自动补全（全额）；退货退款仍须显式传明细。
        List<RefundApplyItemRequest> items = request.getItems();
        if ((items == null || items.isEmpty()) && RefundType.REFUND_ONLY.getType().equals(refundType)) {
            items = buildFullRefundItems(order.getOrderNo());
        }

        String refundNo = OrderNoUtils.getRefundNo();
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setRefundNo(refundNo);
        refundOrder.setOrderNo(order.getOrderNo());
        refundOrder.setUserId(userId);
        refundOrder.setRefundType(refundType);
        refundOrder.setReason(request.getReason().trim());
        refundOrder.setStatus(RefundOrderStatus.APPLYING.getType());
        refundOrder.setLegacyApplyStatus(LEGACY_PENDING);
        refundOrder.setApplyType("USER");
        refundOrder.setRefundAmount(0);
        refundOrderRepository.save(refundOrder);

        int amount = createItemsAndFreeze(refundOrder, items);
        refundOrder.setRefundAmount(amount);
        refundOrderRepository.update(refundOrder);
        // 订单层冻结（三层防线最外层，原子）
        if (orderRepository.freezeOrderRefund(order.getOrderNo(), amount) == 0) {
            throw new BizException("可退额度不足：订单剩余可退金额不够本次申请");
        }

        // CANCEL_BEFORE_SHIP：未发货取消自动受理（无履约成本，无需管理员审批）
        if (RefundType.CANCEL_BEFORE_SHIP.getType().equals(refundType)) {
            refundOrder.setStatus(RefundOrderStatus.APPROVED.getType());
            refundOrder.setLegacyApplyStatus(LEGACY_ACCEPTED);
            refundOrder.setAdminRemark("未发货自动受理");
            refundOrder.setAcceptedTime(new Date());
            refundOrderRepository.update(refundOrder);
            inventoryService.restockForRefund(refundNo, toStockLines(itemsOf(refundNo)), false);
            RefundInfo refundInfo = initiateChannelRefund(refundOrder);
            if (channelHolder != null) channelHolder[0] = refundInfo;
        }

        return detail(refundOrder);
    }

    private RefundApplyVO doUpdate(Long userId, String refundNo, RefundApplyUpdateRequest request) {
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo);
        validateOwner(refundOrder, userId);
        ensureApplying(refundOrder);
        // 释放旧冻结（幂等守卫内），重建明细并重新冻结
        releaseFreezes(refundOrder);
        refundItemRepository.deleteByRefundNo(refundNo);
        refundOrder.setReason(request.getReason().trim());
        int amount = createItemsAndFreeze(refundOrder, request.getItems());
        refundOrder.setRefundAmount(amount);
        refundOrderRepository.update(refundOrder);
        if (orderRepository.freezeOrderRefund(refundOrder.getOrderNo(), amount) == 0) {
            throw new BizException("可退额度不足：订单剩余可退金额不够本次申请");
        }
        return detail(refundOrder);
    }

    private RefundApplyVO doCancelPaidOrder(Long userId, String orderNo, RefundInfo[] channelHolder) {
        OrderInfo order = orderRepository.findByOrderNo(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException("订单不存在或无权访问");
        }
        requirePaidActive(order);
        if (!FulfillmentStatus.WAIT_SHIP.getType().equals(order.getFulfillmentStatus())) {
            throw new BizException("订单已发货，无法取消，请在确认收货后申请退货退款或仅退款");
        }
        RefundApplyRequest request = new RefundApplyRequest();
        request.setOrderNo(orderNo);
        request.setReason("未发货取消订单");
        request.setItems(buildFullRefundItems(orderNo));
        return doCreate(userId, request, RefundType.CANCEL_BEFORE_SHIP.getType(), channelHolder);
    }

    /**
     * 构建整单全额退明细：每个订单明细按剩余可退数量（quantity-已退-冻结）全退。
     */
    private List<RefundApplyItemRequest> buildFullRefundItems(String orderNo) {
        List<OrderItem> orderItems = orderRepository.listItemsByOrderNo(orderNo);
        List<RefundApplyItemRequest> items = new ArrayList<>();
        for (OrderItem oi : orderItems) {
            int refundable = RefundPolicy.refundableQty(oi);
            if (refundable > 0) {
                RefundApplyItemRequest itemReq = new RefundApplyItemRequest();
                itemReq.setOrderItemId(oi.getId());
                itemReq.setQuantity(refundable);
                items.add(itemReq);
            }
        }
        if (items.isEmpty()) {
            throw new BizException("订单无可退明细");
        }
        return items;
    }

    private RefundApplyVO doCreatePriceAdjustment(String orderNo, Integer amount, String reason) {
        OrderInfo order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在");
        }
        requirePaidActive(order);
        if (amount == null || amount <= 0) {
            throw new BizException("差价退款金额必须大于0");
        }
        if (amount > RefundPolicy.orderRefundableAmount(order)) {
            throw new BizException("差价退款金额超过订单剩余可退额度，可退=" + RefundPolicy.orderRefundableAmount(order));
        }
        String refundNo = OrderNoUtils.getRefundNo();
        RefundOrder refundOrder = new RefundOrder();
        refundOrder.setRefundNo(refundNo);
        refundOrder.setOrderNo(order.getOrderNo());
        refundOrder.setUserId(order.getUserId());
        refundOrder.setRefundType(RefundType.PRICE_ADJUSTMENT.getType());
        refundOrder.setReason(reason == null || reason.trim().isEmpty() ? "管理员差价退款" : reason.trim());
        refundOrder.setStatus(RefundOrderStatus.APPLYING.getType());
        refundOrder.setLegacyApplyStatus(LEGACY_PENDING);
        refundOrder.setApplyType("ADMIN");
        refundOrder.setRefundAmount(amount);
        refundOrderRepository.save(refundOrder);
        if (orderRepository.freezeOrderRefund(order.getOrderNo(), amount) == 0) {
            throw new BizException("可退额度不足：订单剩余可退金额不够本次申请");
        }
        return detail(refundOrder);
    }

    // ==================== 内部：撤回/拒绝（释放冻结） ====================

    /**
     * 创建退款明细并逐项冻结（明细层原子；金额服务端计算）。
     */
    private int createItemsAndFreeze(RefundOrder refundOrder, List<RefundApplyItemRequest> requests) {
        if (requests == null || requests.isEmpty()) {
            throw new BizException("至少选择一个退款商品");
        }
        Set<Long> unique = new HashSet<>();
        int amount = 0;
        for (RefundApplyItemRequest req : requests) {
            if (!unique.add(req.getOrderItemId())) {
                throw new BizException("同一订单明细不能重复申请");
            }
            OrderItem orderItem = orderRepository.findItemById(req.getOrderItemId());
            if (orderItem == null || !refundOrder.getOrderNo().equals(orderItem.getOrderNo())) {
                throw new BizException("退款商品明细不属于当前订单");
            }
            int itemAmount = RefundPolicy.calcRefundAmount(orderItem, req.getQuantity());
            // 明细层冻结（数量+金额双上限，原子）
            if (orderRepository.freezeItemRefund(orderItem.getId(), req.getQuantity(), itemAmount) == 0) {
                throw new BizException("可退额度不足，orderItemId=" + orderItem.getId());
            }
            RefundItem item = new RefundItem();
            item.setRefundOrderId(refundOrder.getId());
            item.setRefundNo(refundOrder.getRefundNo());
            item.setOrderItemId(orderItem.getId());
            item.setProductId(orderItem.getProductId());
            item.setUnitPrice(orderItem.getUnitPrice());
            item.setRefundQty(req.getQuantity());
            item.setRefundAmount(itemAmount);
            item.setRestockQty(0);
            item.setLegacyStockReturned(0);
            item.setStatus(RefundOrderStatus.APPLYING.getType());
            refundItemRepository.save(item);
            amount = Math.addExact(amount, itemAmount);
        }
        return amount;
    }

    private void doCancelOrReject(Long userId, String refundNo, String remark, boolean userCancel) {
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo);
        if (refundOrder == null) {
            throw new BizException("退款申请不存在");
        }
        if (userCancel) {
            validateOwner(refundOrder, userId);
        }
        ensureApplying(refundOrder);
        releaseFreezes(refundOrder);
        refundOrder.setStatus(userCancel ? RefundOrderStatus.CANCELLED.getType() : RefundOrderStatus.REJECTED.getType());
        refundOrder.setLegacyApplyStatus(userCancel ? LEGACY_CANCELLED : LEGACY_REJECTED);
        if (!userCancel) {
            refundOrder.setAdminRemark(remark);
        }
        refundOrderRepository.update(refundOrder);
    }

    // ==================== 内部：受理/签收/重试（发起渠道退款） ====================

    /**
     * 释放本退款单占用的明细与订单冻结额度（DB 原子守卫内幂等）。
     */
    private void releaseFreezes(RefundOrder refundOrder) {
        List<RefundItem> items = refundItemRepository.listByRefundNoAsc(refundOrder.getRefundNo());
        for (RefundItem item : items) {
            orderRepository.releaseItemRefundFreeze(item.getOrderItemId(), item.getRefundQty(), item.getRefundAmount());
        }
        if (!isExceptionType(refundOrder.getRefundType()) && nvl(refundOrder.getRefundAmount()) > 0) {
            orderRepository.releaseOrderRefundFreeze(refundOrder.getOrderNo(), refundOrder.getRefundAmount());
        }
    }

    private RefundInfo prepareAccept(String refundNo, String remark, String goodsDisposition) {
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo);
        ensureApplying(refundOrder);

        String type = refundOrder.getRefundType();
        String resolvedDisposition = resolveGoodsDispositionForAccept(refundOrder, goodsDisposition);

        refundOrder.setStatus(RefundOrderStatus.APPROVED.getType());
        refundOrder.setLegacyApplyStatus(LEGACY_ACCEPTED);
        refundOrder.setAdminRemark(remark);
        refundOrder.setGoodsDisposition(resolvedDisposition);
        refundOrder.setAcceptedTime(new Date());
        refundOrderRepository.update(refundOrder);

        if (RefundType.CANCEL_BEFORE_SHIP.getType().equals(type)) {
            return handleCancelBeforeShip(refundNo, refundOrder);
        }
        if (RefundType.RETURN_AND_REFUND.getType().equals(type)) {
            return null;
        }
        if (RefundType.REFUND_ONLY.getType().equals(type)) {
            handleRefundOnly(refundNo, refundOrder);
        }
        return initiateChannelRefund(refundOrder);
    }

    private RefundInfo handleCancelBeforeShip(String refundNo, RefundOrder refundOrder) {
        inventoryService.restockForRefund(refundNo, toStockLines(itemsOf(refundNo)), false);
        return initiateChannelRefund(refundOrder);
    }

    private void handleRefundOnly(String refundNo, RefundOrder refundOrder) {
        OrderInfo order = orderRepository.findByOrderNo(refundOrder.getOrderNo());
        if (order == null) {
            throw new BizException("订单不存在，无法处理退款库存去向");
        }
        boolean received = FulfillmentStatus.RECEIVED.getType().equals(order.getFulfillmentStatus());
        boolean shipped = FulfillmentStatus.SHIPPED.getType().equals(order.getFulfillmentStatus());
        if (shipped) {
            if (RefundGoodsDisposition.RECOVERED.getType().equals(refundOrder.getGoodsDisposition())) {
                inventoryService.restockForRefund(refundNo, toStockLines(itemsOf(refundNo)), false);
                return;
            }
            inventoryService.writeOffLostForRefund(refundNo, toStockLines(itemsOf(refundNo)), false);
            return;
        }
        if (received) {
            inventoryService.writeOffLostForRefund(refundNo, toStockLines(itemsOf(refundNo)), true);
        }
    }

    private RefundInfo prepareConfirmReturn(String refundNo, String remark) {
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo);
        if (refundOrder == null) {
            throw new BizException("退款单不存在");
        }
        if (!RefundOrderStatus.APPROVED.getType().equals(refundOrder.getStatus())) {
            throw new BizException("仅已受理状态允许确认退货签收，当前=" + refundOrder.getStatus());
        }
        if (!RefundPolicy.restockOnConfirmReturn(refundOrder.getRefundType())) {
            throw new BizException("仅退货退款类型需要确认签收");
        }
        inventoryService.restockForRefund(refundNo, toStockLines(itemsOf(refundNo)), true);
        refundOrder.setGoodsDisposition(RefundGoodsDisposition.RECOVERED.getType());
        if (remark != null && !remark.trim().isEmpty()) {
            refundOrder.setAdminRemark(remark.trim());
        }
        return initiateChannelRefund(refundOrder);
    }

    private RefundInfo prepareRetry(String refundNo) {
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo);
        if (refundOrder == null) {
            throw new BizException("退款单不存在");
        }
        if (!RefundOrderStatus.FAILED.getType().equals(refundOrder.getStatus())) {
            throw new BizException("仅退款失败状态允许重试，当前=" + refundOrder.getStatus());
        }
        return initiateChannelRefund(refundOrder);
    }

    @Override
    public RefundApplyVO queryRefundStatus(String refundNo) {
        // 查询/对账路径：无事务包裹且后续含渠道远程调用，普通读即可
        //（for update 在 autocommit 下行锁立即释放，无互斥效果；状态机写入仍在各带锁事务内）。
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo);
        if (refundOrder == null) {
            throw new BizException("退款单不存在");
        }
        // 1. 委托 V1 退款申请服务向渠道发起主动查单并更新 RefundInfo（容错：渠道不可达不阻断本地状态返回）
        String channelError = null;
        try {
            refundApplicationService.queryRefundStatus(refundNo);
        } catch (Exception e) {
            channelError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            log.warn("渠道退款状态查询失败，返回本地状态 refundNo={} err={}", refundNo, channelError);
        }
        // 2. 从 V1 RefundInfo 同步 V2 RefundOrder 状态
        RefundInfo info = refundInfoRepository.findByRefundNo(refundNo);
        if (info != null && info.getRefundStatus() != null) {
            String v2Status = mapV1RefundStatusToV2(info.getRefundStatus());
            if (v2Status != null && !v2Status.equals(refundOrder.getStatus())) {
                refundOrder.setStatus(v2Status);
                refundOrderRepository.update(refundOrder);
                log.info("退款单状态同步 refundNo={} {}→{}", refundNo, refundOrder.getStatus(), v2Status);
            }
        }
        RefundApplyVO vo = detail(refundOrder);
        if (channelError != null) {
            vo.setFailReason("渠道查询失败：" + channelError + "（当前为本地状态，渠道可能未同步）");
        }
        return vo;
    }

    private String mapV1RefundStatusToV2(String v1Status) {
        return V1_TO_V2_STATUS.get(v1Status);
    }

    /**
     * 发起渠道退款前置（事务内）：冻结 PaymentOrder 渠道资金 + RefundOrder→REFUNDING + 建渠道侧 RefundInfo。
     * paymentNo 已存在（重试场景）则不重复冻结。
     */
    private RefundInfo initiateChannelRefund(RefundOrder refundOrder) {
        if (refundOrder.getPaymentNo() == null || refundOrder.getPaymentNo().trim().isEmpty()) {
            PaymentOrder paymentOrder = paymentOrderRepository.findSuccessPaymentOrderForRefund(refundOrder.getOrderNo());
            if (paymentOrder == null) {
                throw new BizException("未找到成功支付单，无法发起渠道退款");
            }
            if (paymentOrderRepository.freezeChannelRefund(paymentOrder.getPaymentNo(), refundOrder.getRefundAmount()) == 0) {
                throw new BizException("渠道可退余额不足，无法发起退款");
            }
            refundOrder.setPaymentNo(paymentOrder.getPaymentNo());
        }
        refundOrder.setStatus(RefundOrderStatus.REFUNDING.getType());
        refundOrderRepository.update(refundOrder);

        RefundInfo existing = refundInfoRepository.findByRefundNo(refundOrder.getRefundNo());
        if (existing != null) {
            return existing;
        }
        OrderInfo order = orderRepository.findByOrderNo(refundOrder.getOrderNo());
        RefundInfo refund = new RefundInfo();
        refund.setOrderNo(refundOrder.getOrderNo());
        refund.setRefundNo(refundOrder.getRefundNo());
        refund.setTotalFee(order.getTotalFee());
        refund.setRefund(refundOrder.getRefundAmount());
        refund.setReason(refundOrder.getReason());
        refund.setApprovalStatus(RefundApprovalStatus.APPROVED.getType());
        refund.setRefundStatus(RefundStatus.CREATED.getType());
        refund.setApproveRemark(refundOrder.getAdminRemark());
        refund.setApprovedTime(new Date());
        refundInfoRepository.save(refund);
        return refund;
    }

    private void executeChannelRefund(OrderInfo order, RefundInfo refund) {
        // 线下/手工收款（管理员标记付款，OFFLINE 支付单）：无渠道资金，渠道退款直接置成功本地结转，
        // 由 RefundSucceeded 事件驱动 settle（冻结转已退、全额退关闭订单），与渠道回调成功路径一致。
        // 此方法运行在事务提交后的渠道调用阶段，普通读即可（不可用 for update：autocommit 下锁立即释放且会在远程调用期间空持锁）。
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refund.getRefundNo());
        if (refundOrder != null && StringUtils.hasText(refundOrder.getPaymentNo())) {
            PaymentOrder paymentOrder = paymentOrderRepository.findByPaymentNo(refundOrder.getPaymentNo());
            if (paymentOrder != null && PaymentConfigGateway.CHANNEL_OFFLINE.equals(paymentOrder.getChannel())) {
                log.info("线下收款订单退款本地结转，refundNo={}, orderNo={}, paymentNo={}",
                    refund.getRefundNo(), refund.getOrderNo(), refundOrder.getPaymentNo());
                refundInfoService.updateRefundToSuccess(refund.getRefundNo(), null,
                    "线下收款（管理员标记付款），无渠道资金退回，退款本地结转");
                return;
            }
        }
        String paymentType = order.getPaymentType();
        if (PayType.WXPAY.getType().equals(paymentType)) {
            wxPayRefundFacade.executeRefund(refund);
        } else if (PayType.ALIPAY.getType().equals(paymentType)) {
            aliPayService.executeRefund(refund);
        } else {
            throw new BizException("不支持的支付类型：" + paymentType);
        }
    }

    // ==================== 内部：结转 ====================

    private void doSettle(String refundNo) {
        RefundOrder refundOrder = refundOrderRepository.findByRefundNo(refundNo);
        if (refundOrder == null) {
            // V1 历史渠道退款无本地退款单，无需结转
            return;
        }
        if (RefundOrderStatus.SUCCESS.getType().equals(refundOrder.getStatus())) {
            return; // 幂等
        }
        // 明细层：冻结转已退（系统冲正单无明细/未冻结时 0 行，守卫幂等）
        List<RefundItem> items = refundItemRepository.listByRefundNoAsc(refundNo);
        for (RefundItem item : items) {
            orderRepository.settleItemRefund(item.getOrderItemId(), item.getRefundQty(), item.getRefundAmount());
        }
        // 订单层：冻结转已退（DUPLICATE/LATE 系统冲正不占售后额度，跳过）
        if (!isExceptionType(refundOrder.getRefundType()) && nvl(refundOrder.getRefundAmount()) > 0) {
            if (orderRepository.settleOrderRefund(refundOrder.getOrderNo(), refundOrder.getRefundAmount()) == 0) {
                log.warn("订单层退款结转未生效（可能已结转或未冻结），refundNo={}", refundNo);
            }
            orderRepository.applyOrderRefundStatus(refundOrder.getOrderNo());
            // 全额退款 → 关闭订单（终态，阻断后续发货/收货等履约操作）；CAS 不满足条件时 0 行幂等。
            orderRepository.casCloseIfFullRefunded(refundOrder.getOrderNo());
        }
        // 渠道层：支付单冻结转已退（用户退款与系统冲正都占渠道资金）
        if (refundOrder.getPaymentNo() != null && !refundOrder.getPaymentNo().trim().isEmpty()
            && nvl(refundOrder.getRefundAmount()) > 0) {
            if (paymentOrderRepository.settleChannelRefund(refundOrder.getPaymentNo(), refundOrder.getRefundAmount()) == 0) {
                log.warn("支付单渠道退款结转未生效，paymentNo={}, refundNo={}", refundOrder.getPaymentNo(), refundNo);
            }
        }
        refundOrder.setStatus(RefundOrderStatus.SUCCESS.getType());
        refundOrder.setLegacyApplyStatus(LEGACY_SUCCESS);
        refundOrder.setSuccessTime(new Date());
        refundOrderRepository.update(refundOrder);
        log.info("退款结转完成，refundNo={}, orderNo={}, amount={}",
            refundNo, refundOrder.getOrderNo(), refundOrder.getRefundAmount());
    }

    // ==================== 校验与辅助 ====================

    /**
     * 已支付有效订单才可退款（V2 四维状态）。
     */
    private void requirePaidActive(OrderInfo order) {
        if (!"PAID".equals(order.getPayStatus()) || !"ACTIVE".equals(order.getOrderStatus())) {
            throw new BizException("当前订单状态不允许退款，orderStatus=" + order.getOrderStatus()
                + "，payStatus=" + order.getPayStatus());
        }
    }

    /**
     * 类型推断：未传类型时按履约状态自动归一。
     */
    private String resolveRefundType(String requested, String fulfillmentStatus) {
        if (requested == null || requested.trim().isEmpty()) {
            return FulfillmentStatus.WAIT_SHIP.getType().equals(fulfillmentStatus)
                ? RefundType.CANCEL_BEFORE_SHIP.getType()
                : RefundType.REFUND_ONLY.getType();
        }
        return validateRefundType(requested.trim(), fulfillmentStatus);
    }

    /**
     * 退款类型与履约状态硬校验（决策 14）。
     */
    private String validateRefundType(String requested, String fulfillmentStatus) {
        RefundType type;
        try {
            type = RefundType.valueOf(requested);
        } catch (IllegalArgumentException e) {
            throw new BizException("不支持的退款类型：" + requested);
        }
        checkFulfillment(type.getType(), fulfillmentStatus);
        return type.getType();
    }

    /**
     * 管理员受理时解析已发货商品去向。
     * SHIPPED + REFUND_ONLY 必须人工选择 LOST/RECOVERED；RECEIVED + REFUND_ONLY 固定为 LOST；
     * 其他退款类型不使用该字段。
     */
    private String resolveGoodsDispositionForAccept(RefundOrder refundOrder, String requested) {
        if (!RefundType.REFUND_ONLY.getType().equals(refundOrder.getRefundType())) {
            return null;
        }
        OrderInfo order = orderRepository.findByOrderNo(refundOrder.getOrderNo());
        if (order == null) {
            throw new BizException("订单不存在，无法确认商品去向");
        }
        if (FulfillmentStatus.SHIPPED.getType().equals(order.getFulfillmentStatus())) {
            if (!StringUtils.hasText(requested)) {
                throw new BizException("商品已发货，管理员受理前必须确认商品去向：LOST 或 RECOVERED");
            }
            String normalized = requested.trim().toUpperCase(Locale.ROOT);
            if (!RefundGoodsDisposition.LOST.getType().equals(normalized)
                && !RefundGoodsDisposition.RECOVERED.getType().equals(normalized)) {
                throw new BizException("不支持的商品去向：" + requested + "，仅支持 LOST/RECOVERED");
            }
            return normalized;
        }
        if (FulfillmentStatus.RECEIVED.getType().equals(order.getFulfillmentStatus())) {
            return RefundGoodsDisposition.LOST.getType();
        }
        return null;
    }

    /**
     * 已解析退款类型 vs 履约状态硬校验（决策 14）。
     */
    private void checkFulfillment(String refundType, String fulfillmentStatus) {
        boolean waitShip = FulfillmentStatus.WAIT_SHIP.getType().equals(fulfillmentStatus);
        boolean shipped = FulfillmentStatus.SHIPPED.getType().equals(fulfillmentStatus);
        boolean received = FulfillmentStatus.RECEIVED.getType().equals(fulfillmentStatus);
        if (RefundType.CANCEL_BEFORE_SHIP.getType().equals(refundType)) {
            if (!waitShip) {
                throw new BizException("订单已发货，无法取消订单，请申请退货退款或仅退款");
            }
            return;
        }
        if (RefundType.RETURN_AND_REFUND.getType().equals(refundType)) {
            if (!received) {
                throw new BizException("确认收货后才能申请退货退款");
            }
            return;
        }
        if (RefundType.REFUND_ONLY.getType().equals(refundType)) {
            if (!shipped && !received) {
                throw new BizException("仅退款需订单已发货或已收货");
            }
            return;
        }
        throw new BizException("不支持的退款类型：" + refundType);
    }

    private void validateOwner(RefundOrder refundOrder, Long userId) {
        if (refundOrder == null || !Objects.equals(refundOrder.getUserId(), userId)) {
            throw new BizException("退款申请不存在或无权访问");
        }
    }

    private void ensureApplying(RefundOrder refundOrder) {
        if (refundOrder == null) {
            throw new BizException("退款申请不存在");
        }
        boolean applying = RefundOrderStatus.APPLYING.getType().equals(refundOrder.getStatus())
            || (LEGACY_PENDING.equals(refundOrder.getLegacyApplyStatus()) && refundOrder.getStatus() == null);
        if (!applying) {
            throw new BizException("仅申请中状态允许该操作，当前状态="
                + (refundOrder.getStatus() != null ? refundOrder.getStatus() : refundOrder.getLegacyApplyStatus()));
        }
    }

    /**
     * 系统自动冲正类型（不占订单售后额度、无订单层冻结）。
     */
    private boolean isExceptionType(String refundType) {
        return RefundType.DUPLICATE_PAYMENT.getType().equals(refundType)
            || RefundType.LATE_PAYMENT.getType().equals(refundType);
    }

    private List<RefundItem> itemsOf(String refundNo) {
        return refundItemRepository.listByRefundNoAsc(refundNo);
    }

    private List<RefundStockLine> toStockLines(List<RefundItem> items) {
        return items.stream()
            .map(i -> new RefundStockLine(i.getOrderItemId(), i.getProductId(), i.getRefundQty()))
            .collect(Collectors.toList());
    }

    private OrderInfo orderNo(String orderNo) {
        OrderInfo order = orderRepository.findByOrderNo(orderNo);
        if (order == null) {
            throw new BizException("订单不存在，orderNo=" + orderNo);
        }
        return order;
    }

    private int nvl(Integer value) {
        return value == null ? 0 : value;
    }

    private RefundApplyVO detail(RefundOrder refundOrder) {
        RefundApplyVO vo = new RefundApplyVO();
        vo.setApply(refundOrder);
        vo.setItems(itemsOf(refundOrder.getRefundNo()));
        OrderInfo order = orderRepository.findByOrderNo(refundOrder.getOrderNo());
        if (order != null) {
            vo.setFulfillmentStatus(order.getFulfillmentStatus());
            vo.setGoodsDispositionRequired(
                RefundOrderStatus.APPLYING.getType().equals(refundOrder.getStatus())
                    && RefundType.REFUND_ONLY.getType().equals(refundOrder.getRefundType())
                    && FulfillmentStatus.SHIPPED.getType().equals(order.getFulfillmentStatus()));
        }
        if (RefundOrderStatus.FAILED.getType().equals(refundOrder.getStatus())) {
            RefundInfo info = refundInfoRepository.findByRefundNo(refundOrder.getRefundNo());
            if (info != null) {
                vo.setFailReason(info.getContentReturn());
            }
        }
        return vo;
    }

    private List<RefundApplyVO> details(List<RefundOrder> list) {
        List<RefundApplyVO> result = new ArrayList<>();
        for (RefundOrder refundOrder : list) {
            result.add(detail(refundOrder));
        }
        return result;
    }
}
