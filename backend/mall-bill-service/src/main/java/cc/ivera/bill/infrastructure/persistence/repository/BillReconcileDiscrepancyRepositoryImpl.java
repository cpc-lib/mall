package cc.ivera.bill.infrastructure.persistence.repository;

import cc.ivera.bill.domain.model.BillReconcileDiscrepancy;
import cc.ivera.bill.domain.repository.BillReconcileDiscrepancyRepository;
import cc.ivera.bill.infrastructure.persistence.converter.BillReconcileDiscrepancyPOConverter;
import cc.ivera.bill.infrastructure.persistence.mapper.BillReconcileDiscrepancyMapper;
import cc.ivera.bill.infrastructure.persistence.po.BillReconcileDiscrepancyPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class BillReconcileDiscrepancyRepositoryImpl implements BillReconcileDiscrepancyRepository {

    private final BillReconcileDiscrepancyMapper discrepancyMapper;

    public BillReconcileDiscrepancyRepositoryImpl(BillReconcileDiscrepancyMapper discrepancyMapper) {
        this.discrepancyMapper = discrepancyMapper;
    }

    @Override
    public void save(BillReconcileDiscrepancy discrepancy) {
        BillReconcileDiscrepancyPO po = BillReconcileDiscrepancyPOConverter.toPO(discrepancy);
        discrepancyMapper.insert(po);
        discrepancy.setId(po.getId());
        discrepancy.setCreateTime(po.getCreateTime());
        discrepancy.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(BillReconcileDiscrepancy discrepancy) {
        discrepancyMapper.updateById(BillReconcileDiscrepancyPOConverter.toPO(discrepancy));
    }

    @Override
    public BillReconcileDiscrepancy findById(Long id) {
        return BillReconcileDiscrepancyPOConverter.toDomain(discrepancyMapper.selectById(id));
    }

    @Override
    public void deleteByImport(Long importId) {
        discrepancyMapper.delete(new LambdaQueryWrapper<BillReconcileDiscrepancyPO>()
            .eq(BillReconcileDiscrepancyPO::getImportId, importId));
    }

    @Override
    public List<BillReconcileDiscrepancy> listByImport(Long importId, String bizType,
                                                       String discrepancyType, String status) {
        return discrepancyMapper.selectDiscrepanciesByImport(importId, bizType, discrepancyType, status).stream()
            .map(BillReconcileDiscrepancyPOConverter::toDomain).collect(Collectors.toList());
    }
}
