package cc.ivera.bill.infrastructure.persistence.repository;

import cc.ivera.bill.domain.model.BillRecord;
import cc.ivera.bill.domain.repository.BillRecordRepository;
import cc.ivera.bill.infrastructure.persistence.converter.BillRecordPOConverter;
import cc.ivera.bill.infrastructure.persistence.mapper.BillRecordMapper;
import cc.ivera.bill.infrastructure.persistence.po.BillRecordPO;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class BillRecordRepositoryImpl implements BillRecordRepository {

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
    public List<BillRecord> listByImport(Long importId, String recordType) {
        return billRecordMapper.selectRecordsByImport(importId, recordType).stream()
            .map(BillRecordPOConverter::toDomain).collect(Collectors.toList());
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
