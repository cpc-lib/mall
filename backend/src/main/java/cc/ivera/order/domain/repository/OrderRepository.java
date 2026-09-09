package cc.ivera.order.domain.repository;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;

import java.util.Date;
import java.util.List;

/**
 * 订单聚合仓储端口：OrderInfo 聚合根 + OrderItem 明细。
 * 状态推进一律走端口内 CAS 条件更新，禁止先改后查；并发互斥由调用方 Redis 分布式锁串行化。
 */
public interface OrderRepository {

    /**
     * 新建订单（id 回填）。
     */
    void save(OrderInfo order);

    /**
     * 新建订单明细（id 回填）。
     */
    void saveItem(OrderItem item);

    OrderInfo findByOrderNo(String orderNo);

    /**
     * 归属校验查询：订单号 + 用户编号精确匹配，不存在返回 null。
     */
    OrderInfo findByOrderNoAndUserId(String orderNo, Long userId);

    /**
     * 查询指定商品 + 支付方式（+支付应用）下最新一笔未支付订单（V1 legacy NOTPAY），不加锁。
     * 并发复用由调用方同维度 Redis 分布式锁串行化保证。
     */
    OrderInfo findLatestNoPayOrder(Long productId, String paymentType, String legacyStatus, Long paymentAppId);

    /**
     * 全量订单按创建时间倒序（V1 订单列表接口）。
     */
    List<OrderInfo> listAllByCreateTimeDesc();

    /**
     * 用户订单按创建时间倒序。
     */
    List<OrderInfo> listByUserIdCreateTimeDesc(Long userId);

    /**
     * 管理员订单动态条件查询（状态等值/订单号模糊/用户精确/创建时间闭区间，全部可选），创建时间倒序。
     */
    List<OrderInfo> searchAdmin(String payStatus, String orderStatus, String fulfillmentStatus,
                                String orderNoLike, Long userId, Date startTime, Date endTime);

    /**
     * 待发货订单：已支付 + 待发货 + 无退款（refund_status 为空或 NONE），按支付时间升序。
     */
    List<OrderInfo> listWaitShip();

    /**
     * 超时未支付扫描：legacy NOTPAY 且创建时间早于 cutoff，按创建时间升序（限量由仓储实现按数据库方言处理）。
     */
    List<OrderInfo> listTimeoutNotPay(Date cutoff);

    /**
     * 订单明细按 id 升序。
     */
    List<OrderItem> listItemsByOrderNo(String orderNo);

    /**
     * 按 id 查询单条订单明细，不存在返回 null。
     */
    OrderItem findItemById(Long itemId);

    /**
     * 幂等保存二维码：仅 code_url 为空（null 或空串）时写入，返回是否更新。
     */
    boolean updateCodeUrlIfAbsent(String orderNo, String codeUrl);

    /**
     * 直接更新 legacy 状态（V1 兼容入口，无 CAS 条件）。
     */
    void updateLegacyStatus(String orderNo, String legacyStatus);

    /**
     * legacy 状态 CAS 更新：仅当前 legacy 状态匹配时落补丁字段；
     * 补丁标记 applyPaidAmountFromTotalFee=true 时同时落 paid_amount = total_fee。
     */
    boolean casLegacyStatus(String orderNo, String currentLegacyStatus, OrderInfo patch);

    /**
     * 履约状态 CAS：order_no + fulfillment_status=from → 置 to，返回受影响行数。
     */
    int casFulfillment(String orderNo, String from, String to);

    // ===== 退款联动 CAS（退款上下文批次4 经订单应用服务调用，闸门为条件 UPDATE）=====

    /**
     * 订单层退款冻结：已退+冻结+本次 不超过实付。
     */
    int freezeOrderRefund(String orderNo, Integer amount);

    /**
     * 释放订单层退款冻结（拒绝/撤回）。
     */
    int releaseOrderRefundFreeze(String orderNo, Integer amount);

    /**
     * 订单层退款结转：冻结转已退。
     */
    int settleOrderRefund(String orderNo, Integer amount);

    /**
     * 按明细汇总回写订单退款汇总状态（幂等）。
     */
    int applyOrderRefundStatus(String orderNo);

    /**
     * 明细层退款冻结（数量+金额双上限，任一不足返回 0）。
     */
    int freezeItemRefund(Long itemId, Integer qty, Integer amount);

    /**
     * 释放明细层退款冻结（拒绝/撤回）。
     */
    int releaseItemRefundFreeze(Long itemId, Integer qty, Integer amount);

    /**
     * 明细层退款结转：冻结转已退。
     */
    int settleItemRefund(Long itemId, Integer qty, Integer amount);

    /**
     * 补库/货损核销累计：restocked+qty 不超过 已退+冻结。
     */
    int addRestockedQty(Long itemId, Integer qty);

    /**
     * 全额退款关单 CAS：订单交易状态 ACTIVE 且退款汇总 FULL_REFUNDED 时置 CLOSED（终态）。
     *
     * @return 1=已关闭；0=不满足全额退款关闭条件（幂等/未全额）
     */
    int casCloseIfFullRefunded(String orderNo);
}
