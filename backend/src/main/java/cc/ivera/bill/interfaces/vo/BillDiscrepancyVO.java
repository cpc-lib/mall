package cc.ivera.bill.interfaces.vo;

import cc.ivera.bill.domain.enums.BillDiscrepancyStatus;
import cc.ivera.bill.domain.enums.BillDiscrepancyType;
import cc.ivera.bill.domain.enums.BillRecordType;
import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 账单对账差异单视图对象，差异类型/业务类型/处理状态均随中文文案透出；金额单位为分。
 */
@Data
public class BillDiscrepancyVO {

    private Long id;

    private Long importId;

    private String billDate;

    /**
     * 业务类型：PAY/REFUND
     */
    private String bizType;

    /**
     * 业务类型中文文案
     */
    private String bizTypeText;

    /**
     * 差异类型枚举 code
     */
    private String discrepancyType;

    /**
     * 差异类型中文文案
     */
    private String discrepancyTypeText;

    /**
     * 业务单号（商户订单号/退款单号）
     */
    private String bizNo;

    /**
     * 渠道流水号
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

    private String channelStatus;

    private String localStatus;

    /**
     * 处理状态：OPEN/RESOLVED
     */
    private String status;

    /**
     * 处理状态中文文案
     */
    private String statusText;

    private String resolveRemark;

    private Date resolvedTime;

    private String resolvedBy;

    private Date createTime;

    public static BillDiscrepancyVO from(BillReconcileDiscrepancy entity) {
        if (entity == null) {
            return null;
        }
        BillDiscrepancyVO vo = new BillDiscrepancyVO();
        vo.setId(entity.getId());
        vo.setImportId(entity.getImportId());
        vo.setBillDate(entity.getBillDate());
        vo.setBizType(entity.getBizType());
        vo.setBizTypeText(BillRecordType.descriptionOf(entity.getBizType()));
        vo.setDiscrepancyType(entity.getDiscrepancyType());
        vo.setDiscrepancyTypeText(BillDiscrepancyType.descriptionOf(entity.getDiscrepancyType()));
        vo.setBizNo(entity.getBizNo());
        vo.setChannelSerialNo(entity.getChannelSerialNo());
        vo.setChannelAmount(entity.getChannelAmount());
        vo.setLocalAmount(entity.getLocalAmount());
        vo.setChannelStatus(entity.getChannelStatus());
        vo.setLocalStatus(entity.getLocalStatus());
        vo.setStatus(entity.getStatus());
        vo.setStatusText(BillDiscrepancyStatus.descriptionOf(entity.getStatus()));
        vo.setResolveRemark(entity.getResolveRemark());
        vo.setResolvedTime(entity.getResolvedTime());
        vo.setResolvedBy(entity.getResolvedBy());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    public static List<BillDiscrepancyVO> from(List<BillReconcileDiscrepancy> entities) {
        List<BillDiscrepancyVO> result = new ArrayList<>();
        if (entities != null) {
            for (BillReconcileDiscrepancy entity : entities) {
                result.add(from(entity));
            }
        }
        return result;
    }
}
