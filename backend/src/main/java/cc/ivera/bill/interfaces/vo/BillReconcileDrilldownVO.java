package cc.ivera.bill.interfaces.vo;

import cc.ivera.bill.domain.model.BillReconcileDrilldown;
import lombok.Data;

import java.util.List;

@Data
public class BillReconcileDrilldownVO {

    private BillDiscrepancyVO discrepancy;
    private List<BillDrilldownRecordVO> channelRecords;
    private List<BillLedgerSnapshotVO> localLedgers;
    private List<BillVerificationItemVO> verificationItems;

    public static BillReconcileDrilldownVO from(BillReconcileDrilldown entity) {
        if (entity == null) {
            return null;
        }
        BillReconcileDrilldownVO vo = new BillReconcileDrilldownVO();
        vo.setDiscrepancy(BillDiscrepancyVO.from(entity.getDiscrepancy()));
        vo.setChannelRecords(BillDrilldownRecordVO.from(entity.getChannelRecords()));
        vo.setLocalLedgers(BillLedgerSnapshotVO.from(entity.getLocalLedgers()));
        vo.setVerificationItems(BillVerificationItemVO.from(entity.getVerificationItems()));
        return vo;
    }
}
