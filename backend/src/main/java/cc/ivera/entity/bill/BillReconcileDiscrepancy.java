package cc.ivera.entity.bill;

import cc.ivera.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 账单对账差异单表：账单支付/退款记录与本地流水核对出的差异。
 * 幂等约束：(import_id, discrepancy_type, biz_type, biz_no) 唯一，重复对账不产生重复差异。
 */
@Data
@TableName("t_bill_reconcile_discrepancy")
public class BillReconcileDiscrepancy extends BaseEntity {

    /**
     * 所属账单导入批次ID
     */
    private Long importId;

    /**
     * 账单日期，格式 yyyy-MM-dd
     */
    private String billDate;

    /**
     * 业务类型：PAY-支付核对，REFUND-退款核对
     */
    private String bizType;

    /**
     * 差异类型：PAY/REFUND × CHANNEL_ONLY/LOCAL_ONLY/AMOUNT_MISMATCH/STATUS_MISMATCH
     */
    private String discrepancyType;

    /**
     * 业务单号：商户订单号或商户退款单号
     */
    private String bizNo;

    /**
     * 渠道流水号：微信订单号/退款单号
     */
    private String channelSerialNo;

    /**
     * 渠道侧金额(分)
     */
    private Integer channelAmount;

    /**
     * 本地侧金额(分)
     */
    private Integer localAmount;

    /**
     * 渠道侧状态
     */
    private String channelStatus;

    /**
     * 本地侧状态
     */
    private String localStatus;

    /**
     * 处理状态：OPEN-待处理，RESOLVED-已处理
     */
    private String status;

    /**
     * 处理备注
     */
    private String resolveRemark;

    /**
     * 处理时间
     */
    private Date resolvedTime;

    /**
     * 处理人
     */
    private String resolvedBy;
}
