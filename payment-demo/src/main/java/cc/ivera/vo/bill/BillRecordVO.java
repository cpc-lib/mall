package cc.ivera.vo.bill;

import cc.ivera.entity.bill.BillRecord;
import cc.ivera.enums.bill.BillRecordType;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 账单流水视图对象，记录类型随中文文案透出；金额单位为分。
 */
@Data
public class BillRecordVO {

    private Long id;

    private Long importId;

    private String channelCode;

    private String billDate;

    /**
     * 记录类型：PAY/REFUND
     */
    private String recordType;

    /**
     * 记录类型中文文案
     */
    private String recordTypeText;

    /**
     * 渠道流水号（微信订单号/退款单号）
     */
    private String channelSerialNo;

    /**
     * 业务单号（商户订单号/退款单号）
     */
    private String bizNo;

    private String tradeType;

    private String tradeStatus;

    /**
     * 支付金额(分)
     */
    private Integer totalAmount;

    /**
     * 退款金额(分)
     */
    private Integer refundAmount;

    private Date tradeTime;

    private Date refundApplyTime;

    private Date refundSuccessTime;

    public static BillRecordVO from(BillRecord entity) {
        if (entity == null) {
            return null;
        }
        BillRecordVO vo = new BillRecordVO();
        vo.setId(entity.getId());
        vo.setImportId(entity.getImportId());
        vo.setChannelCode(entity.getChannelCode());
        vo.setBillDate(entity.getBillDate());
        vo.setRecordType(entity.getRecordType());
        vo.setRecordTypeText(resolveRecordTypeText(entity.getRecordType()));
        vo.setChannelSerialNo(entity.getChannelSerialNo());
        vo.setBizNo(entity.getBizNo());
        vo.setTradeType(entity.getTradeType());
        vo.setTradeStatus(entity.getTradeStatus());
        vo.setTotalAmount(entity.getTotalAmount());
        vo.setRefundAmount(entity.getRefundAmount());
        vo.setTradeTime(entity.getTradeTime());
        vo.setRefundApplyTime(entity.getRefundApplyTime());
        vo.setRefundSuccessTime(entity.getRefundSuccessTime());
        return vo;
    }

    public static List<BillRecordVO> from(List<BillRecord> entities) {
        List<BillRecordVO> result = new ArrayList<>();
        if (entities != null) {
            for (BillRecord entity : entities) {
                result.add(from(entity));
            }
        }
        return result;
    }

    private static String resolveRecordTypeText(String recordType) {
        for (BillRecordType value : BillRecordType.values()) {
            if (value.getType().equals(recordType)) {
                return value.getDescription();
            }
        }
        return recordType;
    }
}
