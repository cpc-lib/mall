package cc.ivera.bill.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 账单流水原始记录表：账单支付行(PAY)/退款行(REFUND)解析后的原始流水。
 * raw_line 保留账单原文行，支持后续追溯。
 */
@Data
@TableName("t_bill_record")
public class BillRecordPO extends BaseEntity {

    /**
     * 所属账单导入批次ID
     */
    private Long importId;

    /**
     * 渠道编码：WXPAY
     */
    private String channelCode;

    /**
     * 账单日期，格式 yyyy-MM-dd
     */
    private String billDate;

    /**
     * 记录类型：PAY-支付，REFUND-退款
     */
    private String recordType;

    /**
     * 渠道流水号：支付为微信订单号，退款为微信退款单号
     */
    private String channelSerialNo;

    /**
     * 业务单号：支付为商户订单号，退款为商户退款单号
     */
    private String bizNo;

    /**
     * 渠道交易类型，如 JSAPI、NATIVE、REFUND
     */
    private String tradeType;

    /**
     * 渠道交易状态，如 SUCCESS
     */
    private String tradeStatus;

    /**
     * 支付金额(分)，支付行取订单金额
     */
    private Integer totalAmount;

    /**
     * 退款金额(分)，退款行取退款金额绝对值
     */
    private Integer refundAmount;

    /**
     * 交易时间
     */
    private Date tradeTime;

    /**
     * 退款申请时间
     */
    private Date refundApplyTime;

    /**
     * 退款成功时间
     */
    private Date refundSuccessTime;

    /**
     * 账单原始行文本
     */
    private String rawLine;
}
