package cc.ivera.product.application.impl;

import cc.ivera.product.application.ProductStockService;
import cc.ivera.product.domain.enums.InventoryBizType;
import cc.ivera.product.domain.model.InventoryTransaction;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.repository.InventoryTransactionRepository;
import cc.ivera.product.domain.repository.ProductRepository;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.web.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * 商品库存调整与库存流水查询。
 * 审计统一走 V2 流水表 t_inventory_transaction；单品调整在同一事务内完成
 * “CAS 扣减/补货 + MANUAL_ADJUST 流水”，保证库存变化与日志原子一致。
 * V4 起：调整申请失败也记录（FAILED 行，available_delta=0，库存不变化），供维护页追溯。
 * 分页采用 count + LIMIT/OFFSET 手动分页（DM8 原生支持，避免方言插件依赖）。
 */
@Service
public class ProductStockServiceImpl implements ProductStockService {

    private final ProductRepository productRepository;
    private final InventoryTransactionRepository transactionRepository;

    public ProductStockServiceImpl(ProductRepository productRepository,
                                   InventoryTransactionRepository transactionRepository) {
        this.productRepository = productRepository;
        this.transactionRepository = transactionRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product adjustOne(Long productId, int delta) {
        if (delta == 0) throw new BizException("库存调整量不能为 0");
        if (productRepository.findById(productId) == null) throw new BizException("商品不存在");
        // 并发权威闸门：available_stock = available_stock + delta 且调整后非负，条件不满足返回 false。
        if (!productRepository.adjustAvailable(productId, delta)) {
            throw new BizException("库存扣减后不能为负数");
        }
        insertTransaction(productId, delta, "SUCCESS", null);
        return productRepository.findById(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFailedAdjustment(Long productId, int delta, String reason) {
        insertTransaction(productId, 0, "FAILED", "申请调整 " + delta + " 被拒绝：" + reason);
    }

    @Override
    public List<InventoryTransaction> recentTransactions(Long productId, int limit) {
        return transactionRepository.recentByProduct(productId, limit);
    }

    @Override
    public PageVO<InventoryTransaction> pageTransactions(int page, int size, Long productId, String bizType, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String bizTypeValue = (bizType != null && !bizType.trim().isEmpty()) ? bizType.trim() : null;
        String statusValue = (status != null && !status.trim().isEmpty()) ? status.trim() : null;
        long total = transactionRepository.countTransactions(productId, bizTypeValue, statusValue);
        List<InventoryTransaction> rows = transactionRepository.pageTransactions(
            productId, bizTypeValue, statusValue, safeSize, (long) (safePage - 1) * safeSize);
        PageVO<InventoryTransaction> result = new PageVO<>();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(rows);
        return result;
    }

    private void insertTransaction(Long productId, int delta, String status, String errorMessage) {
        InventoryTransaction transaction = new InventoryTransaction();
        transaction.setBizNo(InventoryBizType.MANUAL_ADJUST.getType() + ":" + productId + ":" + UUID.randomUUID());
        transaction.setBizType(InventoryBizType.MANUAL_ADJUST.getType());
        transaction.setProductId(productId);
        transaction.setAvailableDelta(delta);
        transaction.setLockedDelta(0);
        transaction.setOperationStatus(status);
        transaction.setErrorMessage(errorMessage);
        transactionRepository.append(transaction);
    }
}
