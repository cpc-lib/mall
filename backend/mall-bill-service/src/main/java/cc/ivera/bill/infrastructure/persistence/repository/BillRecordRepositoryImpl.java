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
}
