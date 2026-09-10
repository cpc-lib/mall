package cc.ivera.bill.interfaces.vo;

import cc.ivera.bill.domain.model.BillLedgerSnapshot;
import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Data
public class BillLedgerSnapshotVO {

    private String ledgerType;
    private String ledgerNo;
    private String bizNo;
    private String orderNo;
    private String channel;
    private String channelSerialNo;
    private Integer amount;
    private String status;
    private Date occurredTime;
    private String sourcePaymentNo;

    public static BillLedgerSnapshotVO from(BillLedgerSnapshot entity) {
        if (entity == null) {
            return null;
        }
        BillLedgerSnapshotVO vo = new BillLedgerSnapshotVO();
        vo.setLedgerType(entity.getLedgerType());
        vo.setLedgerNo(entity.getLedgerNo());
        vo.setBizNo(entity.getBizNo());
        vo.setOrderNo(entity.getOrderNo());
        vo.setChannel(entity.getChannel());
        vo.setChannelSerialNo(entity.getChannelSerialNo());
        vo.setAmount(entity.getAmount());
        vo.setStatus(entity.getStatus());
        vo.setOccurredTime(entity.getOccurredTime());
        vo.setSourcePaymentNo(entity.getSourcePaymentNo());
        return vo;
    }

    public static List<BillLedgerSnapshotVO> from(List<BillLedgerSnapshot> entities) {
        List<BillLedgerSnapshotVO> result = new ArrayList<>();
        if (entities != null) {
            for (BillLedgerSnapshot entity : entities) {
                result.add(from(entity));
            }
        }
        return result;
    }
}
