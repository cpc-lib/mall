package cc.ivera.api.order;

/**
 * 订单退款额度契约：冻结、释放、结转由订单上下文单一写入，
 * 退款上下文只通过本契约申请额度，不得自行写订单退款字段。
 */
public interface OrderRefundQuotaApi {

    /**
     * 订单层退款冻结：已退 + 冻结 + 本次 不超过实付。
     *
     * @return 1=冻结成功；0=可退额度不足
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
     * 明细层退款冻结（数量 + 金额双上限）。
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
     * 明细层回补/核销数量累加守卫：累计回补量 + qty 不超过 已退 + 冻结。
     * 商品服务拆分后由退款/订单上下文在调用库存服务前执行，与退款本地事务同一提交边界。
     *
     * @return 1=累加成功；0=超过可回补上限
     */
    int addRestockedQty(Long itemId, Integer qty);

    /**
     * 全额退款关单 CAS。
     *
     * @return 1=已关闭；0=不满足全额退款关闭条件
     */
    int casCloseIfFullRefunded(String orderNo);
}
