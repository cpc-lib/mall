package cc.ivera.bill.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 单条对账差异的数据下钻结果：渠道原始账、平台账本候选记录和逐字段核验结果。
 */
@Data
public class BillReconcileDrilldown {

    private BillReconcileDiscrepancy discrepancy;

    private List<BillRecord> channelRecords = new ArrayList<>();

    private List<BillLedgerSnapshot> localLedgers = new ArrayList<>();

    private List<BillVerificationItem> verificationItems = new ArrayList<>();
}
