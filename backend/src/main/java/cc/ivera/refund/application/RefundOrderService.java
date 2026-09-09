package cc.ivera.refund.application;

import cc.ivera.refund.interfaces.dto.RefundApplyRequest;
import cc.ivera.refund.interfaces.dto.RefundApplyUpdateRequest;
import cc.ivera.refund.interfaces.vo.RefundApplyVO;

import java.util.List;

/**
 * 退款单域服务（V2）：申请/撤回/审核/退货签收/差价退款/未发货取消/结转。
 * 三层冻结防线：Order 金额 → OrderItem 金额+数量 → PaymentOrder 渠道资金；
 * 退款类型决定库存回补与履约状态硬校验（见 spec 决策 6/14）。
 */
public interface RefundOrderService {

    /**
     * 用户创建退款申请：服务端算金额（尾差规则）+ 冻结 Order/OrderItem，APPLYING。
     */
    RefundApplyVO create(Long userId, RefundApplyRequest request);

    /**
     * 用户编辑 APPLYING 申请：释放旧冻结并按新明细重新冻结。
     */
    RefundApplyVO update(Long userId, String refundNo, RefundApplyUpdateRequest request);

    /**
     * 用户撤回 APPLYING 申请：释放冻结。
     */
    void cancel(Long userId, String refundNo);

    /**
     * 管理员拒绝：REJECTED + 释放冻结（幂等）。
     */
    void reject(String refundNo, String remark);

    /**
     * 管理员受理：APPLYING→APPROVED；
     * CANCEL_BEFORE_SHIP→立即补库存+发起渠道退款；RETURN_AND_REFUND→等待确认签收；其余直接发起渠道退款。
     */
    void accept(String refundNo, String remark);

    /**
     * 管理员退货签收质检（RETURN_AND_REFUND 专用）：补库存 + 发起渠道退款。
     */
    void confirmReturn(String refundNo, String remark);

    /**
     * 管理员重试失败退款（FAILED→REFUNDING，渠道资金已冻结不重复冻结）。
     */
    void retry(String refundNo);

    /**
     * 管理员主动查询渠道退款状态并同步 V2 RefundOrder 状态（REFUNDING/FAILED/SUCCESS）。
     */
    RefundApplyVO queryRefundStatus(String refundNo);

    /**
     * 管理员差价退款（PRICE_ADJUSTMENT）：手填金额受订单剩余可退额度约束，不写明细、不补库存。
     */
    RefundApplyVO createPriceAdjustment(String orderNo, Integer amount, String reason);

    /**
     * 用户已付款未发货取消整单：创建 CANCEL_BEFORE_SHIP 退款单（全额），走受理链路。
     */
    RefundApplyVO cancelPaidOrder(Long userId, String orderNo);

    /**
     * 渠道退款成功结转（RefundSucceededListener 调用，幂等）：
     * RefundOrder→SUCCESS；Order/OrderItem/PaymentOrder 冻结→已退；回写订单 refund_status。
     */
    void settle(String refundNo);

    List<RefundApplyVO> listForUser(Long userId);

    List<RefundApplyVO> listAll();
}
