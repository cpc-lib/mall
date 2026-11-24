package cc.ivera.bill.infrastructure.persistence.repository;

import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.domain.model.BillRecordSummary;
import cc.ivera.bill.domain.repository.BillRecordRepository;
import cc.ivera.bill.infrastructure.persistence.converter.BillRecordPOConverter;
import cc.ivera.bill.infrastructure.persistence.mapper.BillRecordMapper;
import cc.ivera.bill.infrastructure.persistence.po.BillRecordPO;
import cc.ivera.bill.infrastructure.persistence.po.BillRecordSummaryPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class BillRecordRepositoryImpl implements BillRecordRepository {

    private static final int BATCH_SIZE = 500;

    private final BillRecordMapper billRecordMapper;

    public BillRecordRepositoryImpl(BillRecordMapper billRecordMapper) {
        this.billRecordMapper = billRecordMapper;
    }

    @Override
    public void save(BillRecord billRecord) {
        BillRecordPO po = BillRecordPOConverter.toPO(billRecord);
        billRecordMapper.insert(po);
        billRecord.setId(po.getId());
        billRecord.setCreateTime(po.getCreateTime());
        billRecord.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void saveBatch(List<BillRecord> billRecords) {
        if (billRecords == null || billRecords.isEmpty()) {
            return;
        }
        List<BillRecordPO> records = billRecords.stream()
            .map(BillRecordPOConverter::toPO).collect(Collectors.toList());
        for (int from = 0; from < records.size(); from += BATCH_SIZE) {
            billRecordMapper.insertBatch(records.subList(from, Math.min(from + BATCH_SIZE, records.size())));
        }
    }

    @Override
    public List<BillRecord> listByImport(Long importId, String recordType) {
        return billRecordMapper.selectRecordsByImport(importId, recordType).stream()
            .map(BillRecordPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<BillRecord> listForReconcile(Long importId) {
        return billRecordMapper.selectReconcileRecordsByImport(importId).stream()
            .map(BillRecordPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public BillRecordSummary summarizeByImport(Long importId) {
        BillRecordSummaryPO po = billRecordMapper.summarizeByImport(importId);
        BillRecordSummary summary = new BillRecordSummary();
        if (po != null) {
            summary.setPayCount(nullSafe(po.getPayCount()));
            summary.setPayAmount(nullSafe(po.getPayAmount()));
            summary.setRefundCount(nullSafe(po.getRefundCount()));
            summary.setRefundAmount(nullSafe(po.getRefundAmount()));
        }
        return summary;
    }

    private long nullSafe(Long value) {
        return value == null ? 0L : value;
    }

    @Override
    public List<BillRecord> listByImportAndMatch(Long importId, String recordType, String bizNo,
                                                 String channelSerialNo) {
        return billRecordMapper.selectRecordsByImportAndMatch(importId, recordType, bizNo, channelSerialNo).stream()
            .map(BillRecordPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public long countByImport(Long importId, String recordType) {
        Long total = billRecordMapper.countRecordsByImport(importId, recordType);
        return total == null ? 0L : total;
    }

    @Override
    public List<BillRecord> listPageByImport(Long importId, String recordType, int limit, long offset) {
        return billRecordMapper.selectPageByImport(importId, recordType, limit, offset).stream()
            .map(BillRecordPOConverter::toDomain).collect(Collectors.toList());
    }
}
