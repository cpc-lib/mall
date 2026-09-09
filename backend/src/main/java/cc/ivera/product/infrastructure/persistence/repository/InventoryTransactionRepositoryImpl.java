package cc.ivera.product.infrastructure.persistence.repository;

import cc.ivera.product.domain.model.InventoryTransaction;
import cc.ivera.product.domain.repository.InventoryTransactionRepository;
import cc.ivera.product.infrastructure.persistence.converter.InventoryTransactionPOConverter;
import cc.ivera.product.infrastructure.persistence.mapper.InventoryTransactionMapper;
import cc.ivera.product.infrastructure.persistence.po.InventoryTransactionPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

@Repository
public class InventoryTransactionRepositoryImpl implements InventoryTransactionRepository {

    private final InventoryTransactionMapper transactionMapper;

    public InventoryTransactionRepositoryImpl(InventoryTransactionMapper transactionMapper) {
        this.transactionMapper = transactionMapper;
    }

    @Override
    public void append(InventoryTransaction transaction) {
        transactionMapper.insert(InventoryTransactionPOConverter.toPO(transaction));
    }

    @Override
    public int countByBizNo(String bizNo) {
        Integer count = transactionMapper.selectCount(
            new LambdaQueryWrapper<InventoryTransactionPO>().eq(InventoryTransactionPO::getBizNo, bizNo));
        return count == null ? 0 : count;
    }

    @Override
    public long countTransactions(Long productId, String bizType, String status) {
        return transactionMapper.countTransactions(productId, bizType, status);
    }

    @Override
    public List<InventoryTransaction> pageTransactions(Long productId, String bizType, String status, int limit, long offset) {
        return transactionMapper.selectTransactionPage(productId, bizType, status, limit, offset)
            .stream().map(InventoryTransactionPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<InventoryTransaction> recentByProduct(Long productId, int limit) {
        List<InventoryTransactionPO> rows = transactionMapper.selectList(
            new LambdaQueryWrapper<InventoryTransactionPO>().eq(InventoryTransactionPO::getProductId, productId)
                .orderByDesc(InventoryTransactionPO::getCreateTime)
                .orderByDesc(InventoryTransactionPO::getId).last("LIMIT " + limit));
        return rows.stream().map(InventoryTransactionPOConverter::toDomain).collect(Collectors.toList());
    }
}
