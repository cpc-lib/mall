package cc.ivera.bill.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 对账下钻中的平台账本快照，只保留核验所需字段，避免 bill 上下文直接向接口层暴露 payment/refund 聚合对象。
 */
@Data
public class BillLedgerSnapshot {

    /** PAY / REFUND */
    private String ledgerType;

    /** payment_no / refund_no */
    private String ledgerNo;

    /** 平台业务单号：支付为 order_no，退款为 refund_no */
    private String bizNo;

    /** 关联订单号 */
    private String orderNo;

    /** WXPAY / ALIPAY */
    private String channel;

    /** 平台记录的渠道流水号，支付为 channel_order_no；退款当前可能为空 */
    private String channelSerialNo;

    /** 金额(分) */
    private Integer amount;

    /** 平台账本状态 */
    private String status;

    /** 成交/退款成功时间 */
    private Date occurredTime;

    /** 退款来源支付单号；支付账本为空 */
    private String sourcePaymentNo;
}
