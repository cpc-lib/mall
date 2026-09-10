package cc.ivera.bill.interfaces.vo;

import cc.ivera.bill.domain.enums.BillRecordType;
import cc.ivera.bill.domain.model.BillRecord;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 核验下钻专用渠道账视图，包含 rawLine；普通账单流水接口不返回原始行，避免放大列表响应。
 */
@Data
public class BillDrilldownRecordVO {

    private Long id;
    private Long importId;
    private String channelCode;
    private String billDate;
    private String recordType;
    private String recordTypeText;
    private String channelSerialNo;
    private String bizNo;
    private String tradeType;
    private String tradeStatus;
    private Integer totalAmount;
    private Integer refundAmount;
    private Date tradeTime;
    private Date refundApplyTime;
    private Date refundSuccessTime;
    private String rawLine;

    public static BillDrilldownRecordVO from(BillRecord entity) {
        if (entity == null) {
            return null;
        }
        BillDrilldownRecordVO vo = new BillDrilldownRecordVO();
        vo.setId(entity.getId());
        vo.setImportId(entity.getImportId());
        vo.setChannelCode(entity.getChannelCode());
        vo.setBillDate(entity.getBillDate());
        vo.setRecordType(entity.getRecordType());
        vo.setRecordTypeText(BillRecordType.descriptionOf(entity.getRecordType()));
        vo.setChannelSerialNo(entity.getChannelSerialNo());
        vo.setBizNo(entity.getBizNo());
        vo.setTradeType(entity.getTradeType());
        vo.setTradeStatus(entity.getTradeStatus());
        vo.setTotalAmount(entity.getTotalAmount());
        vo.setRefundAmount(entity.getRefundAmount());
        vo.setTradeTime(entity.getTradeTime());
        vo.setRefundApplyTime(entity.getRefundApplyTime());
        vo.setRefundSuccessTime(entity.getRefundSuccessTime());
        vo.setRawLine(entity.getRawLine());
        return vo;
    }

    public static List<BillDrilldownRecordVO> from(List<BillRecord> entities) {
        List<BillDrilldownRecordVO> result = new ArrayList<>();
        if (entities != null) {
            for (BillRecord entity : entities) {
                result.add(from(entity));
            }
        }
        return result;
    }
}
