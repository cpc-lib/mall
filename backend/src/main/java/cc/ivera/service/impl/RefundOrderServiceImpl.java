package cc.ivera.service.impl;

import cc.ivera.domain.refund.RefundPolicy;
import cc.ivera.dto.refund.RefundApplyItemRequest;
import cc.ivera.dto.refund.RefundApplyRequest;
import cc.ivera.dto.refund.RefundApplyUpdateRequest;
import cc.ivera.entity.OrderInfo;
import cc.ivera.entity.OrderItem;
import cc.ivera.entity.PaymentOrder;
import cc.ivera.entity.RefundInfo;
import cc.ivera.entity.RefundItem;
import cc.ivera.entity.RefundOrder;
import cc.ivera.enums.FulfillmentStatus;
import cc.ivera.enums.PaymentOrderStatus;
import cc.ivera.enums.RefundApprovalStatus;
import cc.ivera.enums.RefundOrderStatus;
import cc.ivera.enums.RefundStatus;
import cc.ivera.enums.RefundType;
import cc.ivera.exception.BizException;
import cc.ivera.lock.DistributedLockTemplate;
import cc.ivera.mapper.OrderInfoMapper;
import cc.ivera.mapper.OrderItemMapper;
import cc.ivera.mapper.PaymentOrderMapper;
import cc.ivera.mapper.RefundInfoMapper;
import cc.ivera.mapper.RefundItemMapper;
import cc.ivera.mapper.RefundOrderMapper;
import cc.ivera.service.AliPayService;
import cc.ivera.service.InventoryService;
import cc.ivera.service.RefundApplicationService;
import cc.ivera.service.RefundOrderService;
import cc.ivera.service.wxpay.WxPayRefundFacade;
import cc.ivera.util.OrderNoUtils;
import cc.ivera.vo.RefundApplyVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 退款单域服务实现（V2）。
 * 核心规则：
 * - 三层冻结防线：申请创建冻结 Order+OrderItem（原子 UPDATE，任一不足整体失败）；渠道发起时冻结 PaymentOrder；
 * - 金额服务端计算（RefundPolicy 尾差规则），不信任前端金额；
 * - 退款类型与履约状态硬校验（决策 14）：CANCEL_BEFORE_SHIP 仅 WAIT_SHIP；RETURN_AND_REFUND 仅 RECEIVED；REFUND_ONLY 需 SHIPPED/RECEIVED；
 * - 库存回补：CANCEL_BEFORE_SHIP 受理即补；RETURN_AND_REFUND 签收质检后补；其余不补；
 * - 结转幂等：渠道成功 → 冻结转已退（DB 原子 SQL 守卫天然幂等）。
 */
@Service
@Slf4j
public class RefundOrderServiceImpl implements RefundOrderService {

    private static final String LEGACY_PENDING = "PENDING", LEGACY_ACCEPTED = "ACCEPTED", LEGACY_REJECTED = "REJECTED",
            LEGACY_CANCELLED = "CANCELLED", LEGACY_SUCCESS = "SUCCESS", LEGACY_FAILED = "FAILED";

    private final RefundOrderMapper applyMapper;
    private final RefundItemMapper itemMapper;
    private final OrderInfoMapper orderMapper;
    private final OrderItemMapper orderItemMapper;
    private final RefundInfoMapper refundInfoMapper;
    private final PaymentOrderMapper paymentOrderMapper;
    private final InventoryService inventoryService;
    private final DistributedLockTemplate lockTemplate;
    private final TransactionTemplate tx;
    private final AliPayService aliPayService;
    private final WxPayRefundFacade wxPayRefundFacade;
    private final RefundApplicationService refundApplicationService;

    public RefundOrderServiceImpl(RefundOrderMapper applyMapper,
                                  RefundItemMapper itemMapper,
                                  OrderInfoMapper orderMapper,
                                  OrderItemMapper orderItemMapper,
                                  RefundInfoMapper refundInfoMapper,
                                  PaymentOrderMapper paymentOrderMapper,
                                  InventoryService inventoryService,
                                  DistributedLockTemplate lockTemplate,
                                  TransactionTemplate tx,
                                  AliPayService aliPayService,
                                  WxPayRefundFacade wxPayRefundFacade,
                                  RefundApplicationService refundApplicationService) {
        this.applyMapper = applyMapper;
        this.itemMapper = itemMapper;
        this.orderMapper = orderMapper;
        this.orderItemMapper = orderItemMapper;
        this.refundInfoMapper = refundInfoMapper;
        this.paymentOrderMapper = paymentOrderMapper;
        this.inventoryService = inventoryService;
        this.lockTemplate = lockTemplate;
        this.tx = tx;
        this.aliPayService = aliPayService;
        this.wxPayRefundFacade = wxPayRefundFacade;
        this.refundApplicationService = refundApplicationService;
    }

    // ==================== 用户端：申请 / 编辑 / 撤回 / 未发货取消 ====================

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

    // ==================== 管理端：拒绝 / 受理 / 签收 / 重试 / 差价退款 ====================

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
    public void accept(String refundNo, String remark) {
        lockTemplate.execute("refund:accept:" + refundNo, 5000L, -1L, () -> {
            RefundInfo channelRefund = tx.execute(s -> prepareAccept(refundNo, remark));
            if (channelRefund != null) {
                // 渠道 HTTP 调用在事务提交后执行（沿用既有模式）
                executeChannelRefund(orderNo(channelRefund.getOrderNo()), channelRefund);
            }
            return null;
        });
    }

    @Override
    public void confirmReturn(String refundNo, String remark) {
        lockTemplate.execute("refund:confirm-return:" + refundNo, 5000L, -1L, () -> {
            RefundInfo channelRefund = tx.execute(s -> prepareConfirmReturn(refundNo, remark));
            if (channelRefund != null) {
                executeChannelRefund(orderNo(channelRefund.getOrderNo()), channelRefund);
            }
            return null;
        });
    }

    @Override
    public void retry(String refundNo) {
        lockTemplate.execute("refund:retry:" + refundNo, 5000L, -1L, () -> {
            RefundInfo channelRefund = tx.execute(s -> prepareRetry(refundNo));
            if (channelRefund != null) {
                executeChannelRefund(orderNo(channelRefund.getOrderNo()), channelRefund);
            }
            return null;
        });
    }

    @Override
    public RefundApplyVO createPriceAdjustment(String orderNo, Integer amount, String reason) {
        return lockTemplate.execute("refund:create:" + orderNo, 5000L, -1L, () ->
                tx.execute(s -> doCreatePriceAdjustment(orderNo, amount, reason)));
    }

    // ==================== 结转（渠道退款成功，幂等） ====================

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

    // ==================== 查询 ====================

    @Override
    public List<RefundApplyVO> listForUser(Long userId) {
        QueryWrapper<RefundOrder> q = new QueryWrapper<>();
        q.eq("user_id", userId).orderByDesc("create_time");
        return details(applyMapper.selectList(q));
    }

    @Override
    public List<RefundApplyVO> listAll() {
        return details(applyMapper.selectList(new QueryWrapper<RefundOrder>().orderByDesc("create_time")));
    }

    // ==================== 内部：创建链路 ====================

    private RefundApplyVO doCreate(Long userId, RefundApplyRequest request, String forcedType, RefundInfo[] channelHolder) {
        OrderInfo order = orderMapper.selectByOrderNoForUpdate(request.getOrderNo());
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException("订单不存在或无权访问");
        }
        requirePaidActive(order);
        String refundType = forcedType != null ? forcedType : resolveRefundType(request.getRefundType(), order.getFulfillmentStatus());
        checkFulfillment(refundType, order.getFulfillmentStatus());

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
        applyMapper.insert(refundOrder);

        int amount = createItemsAndFreeze(refundOrder, request.getItems());
        refundOrder.setRefundAmount(amount);
        applyMapper.updateById(refundOrder);
        // 订单层冻结（三层防线最外层，原子）
        if (orderMapper.freezeOrderRefund(order.getOrderNo(), amount) == 0) {
            throw new BizException("可退额度不足：订单剩余可退金额不够本次申请");
        }

        // CANCEL_BEFORE_SHIP：未发货取消自动受理（无履约成本，无需管理员审批）
        if (RefundType.CANCEL_BEFORE_SHIP.getType().equals(refundType)) {
            refundOrder.setStatus(RefundOrderStatus.APPROVED.getType());
            refundOrder.setLegacyApplyStatus(LEGACY_ACCEPTED);
            refundOrder.setAdminRemark("未发货自动受理");
            refundOrder.setAcceptedTime(new Date());
            applyMapper.updateById(refundOrder);
            inventoryService.restockForRefund(refundNo, itemsOf(refundNo), false);
            RefundInfo refundInfo = initiateChannelRefund(refundOrder);
            if (channelHolder != null) channelHolder[0] = refundInfo;
        }

        return detail(refundOrder);
    }

    private RefundApplyVO doUpdate(Long userId, String refundNo, RefundApplyUpdateRequest request) {
        RefundOrder refundOrder = applyMapper.selectByRefundNoForUpdate(refundNo);
        validateOwner(refundOrder, userId);
        ensureApplying(refundOrder);
        // 释放旧冻结（幂等守卫内），重建明细并重新冻结
        releaseFreezes(refundOrder);
        itemMapper.delete(new QueryWrapper<RefundItem>().eq("refund_no", refundNo));
        refundOrder.setReason(request.getReason().trim());
        int amount = createItemsAndFreeze(refundOrder, request.getItems());
        refundOrder.setRefundAmount(amount);
        applyMapper.updateById(refundOrder);
        if (orderMapper.freezeOrderRefund(refundOrder.getOrderNo(), amount) == 0) {
            throw new BizException("可退额度不足：订单剩余可退金额不够本次申请");
        }
        return detail(refundOrder);
    }

    private RefundApplyVO doCancelPaidOrder(Long userId, String orderNo, RefundInfo[] channelHolder) {
        OrderInfo order = orderMapper.selectByOrderNoForUpdate(orderNo);
        if (order == null || !Objects.equals(order.getUserId(), userId)) {
            throw new BizException("订单不存在或无权访问");
        }
        requirePaidActive(order);
        if (!FulfillmentStatus.WAIT_SHIP.getType().equals(order.getFulfillmentStatus())) {
            throw new BizException("订单已发货，无法取消，请在确认收货后申请退货退款或仅退款");
        }
        List<OrderItem> orderItems = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_no", orderNo).orderByAsc("id"));
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
        RefundApplyRequest request = new RefundApplyRequest();
        request.setOrderNo(orderNo);
        request.setReason("未发货取消订单");
        request.setItems(items);
        return doCreate(userId, request, RefundType.CANCEL_BEFORE_SHIP.getType(), channelHolder);
    }

    private RefundApplyVO doCreatePriceAdjustment(String orderNo, Integer amount, String reason) {
        OrderInfo order = orderMapper.selectByOrderNoForUpdate(orderNo);
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
        applyMapper.insert(refundOrder);
        if (orderMapper.freezeOrderRefund(order.getOrderNo(), amount) == 0) {
            throw new BizException("可退额度不足：订单剩余可退金额不够本次申请");
        }
        return detail(refundOrder);
    }

    /** 创建退款明细并逐项冻结（明细层原子；金额服务端计算）。 */
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
            OrderItem orderItem = orderItemMapper.selectById(req.getOrderItemId());
            if (orderItem == null || !refundOrder.getOrderNo().equals(orderItem.getOrderNo())) {
                throw new BizException("退款商品明细不属于当前订单");
            }
            int itemAmount = RefundPolicy.calcRefundAmount(orderItem, req.getQuantity());
            // 明细层冻结（数量+金额双上限，原子）
            if (orderItemMapper.freezeItemRefund(orderItem.getId(), req.getQuantity(), itemAmount) == 0) {
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
            itemMapper.insert(item);
            amount = Math.addExact(amount, itemAmount);
        }
        return amount;
    }

    // ==================== 内部：撤回/拒绝（释放冻结） ====================

    private void doCancelOrReject(Long userId, String refundNo, String remark, boolean userCancel) {
        RefundOrder refundOrder = applyMapper.selectByRefundNoForUpdate(refundNo);
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
        applyMapper.updateById(refundOrder);
    }

    /** 释放本退款单占用的明细与订单冻结额度（DB 原子守卫内幂等）。 */
    private void releaseFreezes(RefundOrder refundOrder) {
        List<RefundItem> items = itemMapper.selectList(
                new QueryWrapper<RefundItem>().eq("refund_no", refundOrder.getRefundNo()));
        for (RefundItem item : items) {
            orderItemMapper.releaseItemRefundFreeze(item.getOrderItemId(), item.getRefundQty(), item.getRefundAmount());
        }
        if (!isExceptionType(refundOrder.getRefundType()) && nvl(refundOrder.getRefundAmount()) > 0) {
            orderMapper.releaseOrderRefundFreeze(refundOrder.getOrderNo(), refundOrder.getRefundAmount());
        }
    }

    // ==================== 内部：受理/签收/重试（发起渠道退款） ====================

    private RefundInfo prepareAccept(String refundNo, String remark) {
        RefundOrder refundOrder = applyMapper.selectByRefundNoForUpdate(refundNo);
        ensureApplying(refundOrder);
        refundOrder.setStatus(RefundOrderStatus.APPROVED.getType());
        refundOrder.setLegacyApplyStatus(LEGACY_ACCEPTED);
        refundOrder.setAdminRemark(remark);
        refundOrder.setAcceptedTime(new Date());
        applyMapper.updateById(refundOrder);

        String type = refundOrder.getRefundType();
        if (RefundType.CANCEL_BEFORE_SHIP.getType().equals(type)) {
            // 未发货取消：受理即回补库存（未收货：锁定→可用）
            inventoryService.restockForRefund(refundNo, itemsOf(refundNo), false);
            return initiateChannelRefund(refundOrder);
        }
        if (RefundType.RETURN_AND_REFUND.getType().equals(type)) {
            // 退货退款：等待管理员确认签收质检后再发起渠道退款
            return null;
        }
        // REFUND_ONLY / PRICE_ADJUSTMENT：直接发起渠道退款
        return initiateChannelRefund(refundOrder);
    }

    private RefundInfo prepareConfirmReturn(String refundNo, String remark) {
        RefundOrder refundOrder = applyMapper.selectByRefundNoForUpdate(refundNo);
        if (refundOrder == null) {
            throw new BizException("退款单不存在");
        }
        if (!RefundOrderStatus.APPROVED.getType().equals(refundOrder.getStatus())) {
            throw new BizException("仅已受理状态允许确认退货签收，当前=" + refundOrder.getStatus());
        }
        if (!RefundPolicy.restockOnConfirmReturn(refundOrder.getRefundType())) {
            throw new BizException("仅退货退款类型需要确认签收");
        }
        inventoryService.restockForRefund(refundNo, itemsOf(refundNo), true);
        if (remark != null && !remark.trim().isEmpty()) {
            refundOrder.setAdminRemark(remark.trim());
        }
        return initiateChannelRefund(refundOrder);
    }

    private RefundInfo prepareRetry(String refundNo) {
        RefundOrder refundOrder = applyMapper.selectByRefundNoForUpdate(refundNo);
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
        RefundOrder refundOrder = applyMapper.selectByRefundNoForUpdate(refundNo);
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
        RefundInfo info = refundInfoMapper.selectOne(
                new QueryWrapper<RefundInfo>().eq("refund_no", refundNo).last("LIMIT 1"));
        if (info != null && info.getRefundStatus() != null) {
            String v2Status = mapV1RefundStatusToV2(info.getRefundStatus());
            if (v2Status != null && !v2Status.equals(refundOrder.getStatus())) {
                refundOrder.setStatus(v2Status);
                applyMapper.updateById(refundOrder);
                log.info("退款单状态同步 refundNo={} {}→{}", refundNo, refundOrder.getStatus(), v2Status);
            }
        }
        RefundApplyVO vo = detail(refundOrder);
        if (channelError != null) {
            vo.setFailReason("渠道查询失败：" + channelError + "（当前为本地状态，渠道可能未同步）");
        }
        return vo;
    }

    /** V1 RefundStatus → V2 RefundOrderStatus 映射（仅同步渠道可达终态）。 */
    private String mapV1RefundStatusToV2(String v1Status) {
        if (RefundStatus.SUCCESS.getType().equals(v1Status)) return RefundOrderStatus.SUCCESS.getType();
        if (RefundStatus.FAILED.getType().equals(v1Status)) return RefundOrderStatus.FAILED.getType();
        if (RefundStatus.ABNORMAL.getType().equals(v1Status)) return RefundOrderStatus.FAILED.getType();
        return null; // PROCESSING/CREATED 等中间态不同步
    }

    /**
     * 发起渠道退款前置（事务内）：冻结 PaymentOrder 渠道资金 + RefundOrder→REFUNDING + 建渠道侧 RefundInfo。
     * paymentNo 已存在（重试场景）则不重复冻结。
     */
    private RefundInfo initiateChannelRefund(RefundOrder refundOrder) {
        if (refundOrder.getPaymentNo() == null || refundOrder.getPaymentNo().trim().isEmpty()) {
            PaymentOrder paymentOrder = findSuccessPaymentOrder(refundOrder.getOrderNo());
            if (paymentOrder == null) {
                throw new BizException("未找到成功支付单，无法发起渠道退款");
            }
            if (paymentOrderMapper.freezeChannelRefund(paymentOrder.getPaymentNo(), refundOrder.getRefundAmount()) == 0) {
                throw new BizException("渠道可退余额不足，无法发起退款");
            }
            refundOrder.setPaymentNo(paymentOrder.getPaymentNo());
        }
        refundOrder.setStatus(RefundOrderStatus.REFUNDING.getType());
        applyMapper.updateById(refundOrder);

        RefundInfo existing = refundInfoMapper.selectOne(
                new QueryWrapper<RefundInfo>().eq("refund_no", refundOrder.getRefundNo()));
        if (existing != null) {
            return existing;
        }
        OrderInfo order = orderMapper.selectOne(
                new QueryWrapper<OrderInfo>().eq("order_no", refundOrder.getOrderNo()));
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
        refundInfoMapper.insert(refund);
        return refund;
    }

    private void executeChannelRefund(OrderInfo order, RefundInfo refund) {
        String paymentType = order.getPaymentType();
        if (cc.ivera.enums.PayType.WXPAY.getType().equals(paymentType)) {
            wxPayRefundFacade.executeRefund(refund);
        } else if (cc.ivera.enums.PayType.ALIPAY.getType().equals(paymentType)) {
            aliPayService.executeRefund(refund);
        } else {
            throw new BizException("不支持的支付类型：" + paymentType);
        }
    }

    // ==================== 内部：结转 ====================

    private void doSettle(String refundNo) {
        RefundOrder refundOrder = applyMapper.selectByRefundNoForUpdate(refundNo);
        if (refundOrder == null) {
            // V1 历史渠道退款无本地退款单，无需结转
            return;
        }
        if (RefundOrderStatus.SUCCESS.getType().equals(refundOrder.getStatus())) {
            return; // 幂等
        }
        // 明细层：冻结转已退（系统冲正单无明细/未冻结时 0 行，守卫幂等）
        List<RefundItem> items = itemMapper.selectList(
                new QueryWrapper<RefundItem>().eq("refund_no", refundNo));
        for (RefundItem item : items) {
            orderItemMapper.settleItemRefund(item.getOrderItemId(), item.getRefundQty(), item.getRefundAmount());
        }
        // 订单层：冻结转已退（DUPLICATE/LATE 系统冲正不占售后额度，跳过）
        if (!isExceptionType(refundOrder.getRefundType()) && nvl(refundOrder.getRefundAmount()) > 0) {
            if (orderMapper.settleOrderRefund(refundOrder.getOrderNo(), refundOrder.getRefundAmount()) == 0) {
                log.warn("订单层退款结转未生效（可能已结转或未冻结），refundNo={}", refundNo);
            }
            orderMapper.applyOrderRefundStatus(refundOrder.getOrderNo());
            // 全额退款 → 关闭订单（终态，阻断后续发货/收货等履约操作）
            OrderInfo after = orderMapper.selectOne(new QueryWrapper<OrderInfo>().eq("order_no", refundOrder.getOrderNo()));
            if (after != null && "FULL_REFUNDED".equals(after.getRefundStatus()) && "ACTIVE".equals(after.getOrderStatus())) {
                orderMapper.update(null, new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<OrderInfo>()
                        .eq("order_no", refundOrder.getOrderNo())
                        .eq("order_status", "ACTIVE")
                        .set("order_status", "CLOSED")
                        .set("update_time", new Date()));
            }
        }
        // 渠道层：支付单冻结转已退（用户退款与系统冲正都占渠道资金）
        if (refundOrder.getPaymentNo() != null && !refundOrder.getPaymentNo().trim().isEmpty()
                && nvl(refundOrder.getRefundAmount()) > 0) {
            if (paymentOrderMapper.settleChannelRefund(refundOrder.getPaymentNo(), refundOrder.getRefundAmount()) == 0) {
                log.warn("支付单渠道退款结转未生效，paymentNo={}, refundNo={}", refundOrder.getPaymentNo(), refundNo);
            }
        }
        refundOrder.setStatus(RefundOrderStatus.SUCCESS.getType());
        refundOrder.setLegacyApplyStatus(LEGACY_SUCCESS);
        refundOrder.setSuccessTime(new Date());
        applyMapper.updateById(refundOrder);
        log.info("退款结转完成，refundNo={}, orderNo={}, amount={}",
                refundNo, refundOrder.getOrderNo(), refundOrder.getRefundAmount());
    }

    // ==================== 校验与辅助 ====================

    /** 已支付有效订单才可退款（V2 四维状态）。 */
    private void requirePaidActive(OrderInfo order) {
        if (!"PAID".equals(order.getPayStatus()) || !"ACTIVE".equals(order.getOrderStatus())) {
            throw new BizException("当前订单状态不允许退款，orderStatus=" + order.getOrderStatus()
                    + "，payStatus=" + order.getPayStatus());
        }
    }

    /** 类型推断：未传类型时按履约状态自动归一。 */
    private String resolveRefundType(String requested, String fulfillmentStatus) {
        if (requested == null || requested.trim().isEmpty()) {
            return FulfillmentStatus.WAIT_SHIP.getType().equals(fulfillmentStatus)
                    ? RefundType.CANCEL_BEFORE_SHIP.getType()
                    : RefundType.REFUND_ONLY.getType();
        }
        return validateRefundType(requested.trim(), fulfillmentStatus);
    }

    /** 退款类型与履约状态硬校验（决策 14）。 */
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

    /** 已解析退款类型 vs 履约状态硬校验（决策 14）。 */
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

    /** 系统自动冲正类型（不占订单售后额度、无订单层冻结）。 */
    private boolean isExceptionType(String refundType) {
        return RefundType.DUPLICATE_PAYMENT.getType().equals(refundType)
                || RefundType.LATE_PAYMENT.getType().equals(refundType);
    }

    private PaymentOrder findSuccessPaymentOrder(String orderNo) {
        List<PaymentOrder> list = paymentOrderMapper.selectList(new QueryWrapper<PaymentOrder>()
                .eq("order_no", orderNo)
                .eq("status", PaymentOrderStatus.SUCCESS.getType())
                .orderByDesc("paid_time"));
        if (!list.isEmpty()) {
            return list.get(0);
        }
        // 对账兜底：订单已 PAID 但支付单卡在 PAYING（旧代码支付成功未推进 PaymentOrder），
        // 此时订单级状态已是成交，支付单可安全推进为 SUCCESS。
        List<PaymentOrder> paying = paymentOrderMapper.selectList(new QueryWrapper<PaymentOrder>()
                .eq("order_no", orderNo)
                .eq("status", PaymentOrderStatus.PAYING.getType())
                .orderByDesc("id"));
        if (paying.isEmpty()) {
            return null;
        }
        PaymentOrder stuck = paying.get(0);
        log.warn("支付单卡在 PAYING 但订单已成交，对账推进 ===> paymentNo={}, orderNo={}", stuck.getPaymentNo(), orderNo);
        PaymentOrder fix = new PaymentOrder();
        fix.setStatus(PaymentOrderStatus.SUCCESS.getType());
        fix.setPaidAmount(stuck.getRequestAmount());
        fix.setPaidTime(new Date());
        paymentOrderMapper.update(fix, new QueryWrapper<PaymentOrder>()
                .eq("payment_no", stuck.getPaymentNo())
                .eq("status", PaymentOrderStatus.PAYING.getType()));
        stuck.setStatus(PaymentOrderStatus.SUCCESS.getType());
        return stuck;
    }

    private List<RefundItem> itemsOf(String refundNo) {
        return itemMapper.selectList(new QueryWrapper<RefundItem>().eq("refund_no", refundNo).orderByAsc("id"));
    }

    private OrderInfo orderNo(String orderNo) {
        OrderInfo order = orderMapper.selectOne(new QueryWrapper<OrderInfo>().eq("order_no", orderNo));
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
        if (RefundOrderStatus.FAILED.getType().equals(refundOrder.getStatus())) {
            RefundInfo info = refundInfoMapper.selectOne(
                    new QueryWrapper<RefundInfo>().eq("refund_no", refundOrder.getRefundNo()).last("LIMIT 1"));
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
