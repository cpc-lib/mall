package cc.ivera.bill.infrastructure.persistence.repository;

import cc.ivera.bill.domain.model.BillImport;
import cc.ivera.bill.domain.repository.BillImportRepository;
import cc.ivera.bill.infrastructure.persistence.converter.BillImportPOConverter;
import cc.ivera.bill.infrastructure.persistence.mapper.BillImportMapper;
import cc.ivera.bill.infrastructure.persistence.po.BillImportPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class BillImportRepositoryImpl implements BillImportRepository {

    private final BillImportMapper billImportMapper;

    public BillImportRepositoryImpl(BillImportMapper billImportMapper) {
        this.billImportMapper = billImportMapper;
    }

    @Override
    public void save(BillImport billImport) {
        BillImportPO po = BillImportPOConverter.toPO(billImport);
        billImportMapper.insert(po);
        billImport.setId(po.getId());
        billImport.setCreateTime(po.getCreateTime());
        billImport.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(BillImport billImport) {
        billImportMapper.updateById(BillImportPOConverter.toPO(billImport));
    }

    @Override
    public BillImport findById(Long id) {
        return BillImportPOConverter.toDomain(billImportMapper.selectById(id));
    }

    @Override
    public BillImport findByImportNo(String importNo) {
        return BillImportPOConverter.toDomain(billImportMapper.selectOne(new LambdaQueryWrapper<BillImportPO>()
            .eq(BillImportPO::getImportNo, importNo)));
    }

    @Override
    public BillImport findByFileHash(String fileHash) {
        return BillImportPOConverter.toDomain(billImportMapper.selectOne(new LambdaQueryWrapper<BillImportPO>()
            .eq(BillImportPO::getFileHash, fileHash)));
    }

    @Override
    public List<BillImport> listByChannelDate(String channelCode, String billType, String billDate) {
        return billImportMapper.selectList(new LambdaQueryWrapper<BillImportPO>()
                .eq(BillImportPO::getChannelCode, channelCode)
                .eq(BillImportPO::getBillType, billType)
                .eq(BillImportPO::getBillDate, billDate)
                .orderByAsc(BillImportPO::getId))
            .stream().map(BillImportPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<BillImport> listByDate(String billDate) {
        return billImportMapper.selectImportsByDate(billDate).stream()
            .map(BillImportPOConverter::toDomain).collect(Collectors.toList());
    }
}
