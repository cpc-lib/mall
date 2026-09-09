package cc.ivera.payment.application;

/**
 * 统一支付成功处理器（V2）。
 * 微信 V2/V3 notify、支付宝 notify、主动查单同步共用：
 * 1. 支付单幂等（按 payment_no 状态机）；
 * 2. 订单 CAS NOTPAY → SUCCESS（含四维状态/实付金额落库、事务后提交库存预占）；
 * 3. 订单已成交：不同支付单 → 重复支付自动原路退款；
 * 4. 订单已关闭：晚到支付自动原路退款。
 * <p>
 * 调用方负责分布式锁 + 事务包裹（与既有 notify 处理结构一致）。
 */
public interface PaymentSuccessService {

    /**
     * @param orderNo        商户订单号
     * @param channel        支付渠道（WXPAY/ALIPAY）
     * @param channelOrderNo 渠道侧交易号（微信 transaction_id / 支付宝 trade_no），可为空
     * @param paidAmount     实付金额(分)，可为空（空则取订单 totalFee）
     * @return true=本次调用完成了订单 NOTPAY→SUCCESS 的首次成交推进（调用方应写支付流水）；
     * false=幂等命中/重复支付冲正/晚到支付冲正/并发 CAS 失败
     */
    boolean handlePaymentSuccess(String orderNo, String channel, String channelOrderNo, Integer paidAmount);
}
