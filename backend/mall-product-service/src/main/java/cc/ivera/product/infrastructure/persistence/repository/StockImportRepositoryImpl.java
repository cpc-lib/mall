package cc.ivera.product.infrastructure.persistence.repository;

import cc.ivera.product.domain.model.StockImport;
import cc.ivera.product.domain.repository.StockImportRepository;
import cc.ivera.product.infrastructure.persistence.converter.StockImportPOConverter;
import cc.ivera.product.infrastructure.persistence.mapper.StockImportMapper;
import cc.ivera.product.infrastructure.persistence.po.StockImportPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Repository
public class StockImportRepositoryImpl implements StockImportRepository {

    private final StockImportMapper stockImportMapper;

    public StockImportRepositoryImpl(StockImportMapper stockImportMapper) {
        this.stockImportMapper = stockImportMapper;
    }

    @Override
    public void insert(StockImport record) {
        StockImportPO po = StockImportPOConverter.toPO(record);
        stockImportMapper.insert(po);
        record.setId(po.getId());
        record.setCreateTime(po.getCreateTime());
        record.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(StockImport record) {
        stockImportMapper.updateById(StockImportPOConverter.toPO(record));
    }

    @Override
    public StockImport findById(Long id) {
        return StockImportPOConverter.toDomain(stockImportMapper.selectById(id));
    }

    @Override
    public List<StockImport> findRecent(int limit) {
        List<StockImportPO> rows = stockImportMapper.selectList(
            new LambdaQueryWrapper<StockImportPO>().orderByDesc(StockImportPO::getCreateTime)
                .orderByDesc(StockImportPO::getId).last("LIMIT " + limit));
        return rows.stream().map(StockImportPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public boolean updatePendingItems(Long id, int itemCount, String itemsJson, Date now) {
        LambdaUpdateWrapper<StockImportPO> uw = new LambdaUpdateWrapper<>();
        uw.eq(StockImportPO::getId, id).eq(StockImportPO::getStatus, StockImport.STATUS_PENDING)
            .set(StockImportPO::getItemCount, itemCount)
            .set(StockImportPO::getItemsJson, itemsJson)
            .set(StockImportPO::getUpdateTime, now);
        return stockImportMapper.update(null, uw) > 0;
    }

    @Override
    public boolean casConfirm(Long id, Date confirmTime) {
        LambdaUpdateWrapper<StockImportPO> uw = new LambdaUpdateWrapper<>();
        uw.eq(StockImportPO::getId, id).eq(StockImportPO::getStatus, StockImport.STATUS_PENDING)
            .set(StockImportPO::getStatus, StockImport.STATUS_CONFIRMED)
            .set(StockImportPO::getConfirmTime, confirmTime);
        return stockImportMapper.update(null, uw) > 0;
    }
}
