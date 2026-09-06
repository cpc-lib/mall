package cc.ivera.service.impl;

import cc.ivera.entity.InventoryTransaction;
import cc.ivera.entity.Product;
import cc.ivera.enums.InventoryBizType;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.InventoryTransactionMapper;
import cc.ivera.mapper.ProductMapper;
import cc.ivera.service.ProductStockService;
import cc.ivera.vo.InventoryTransactionVO;
import cc.ivera.vo.PageVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
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

    private final ProductMapper productMapper;
    private final InventoryTransactionMapper inventoryTransactionMapper;

    public ProductStockServiceImpl(ProductMapper productMapper, InventoryTransactionMapper inventoryTransactionMapper) {
        this.productMapper = productMapper;
        this.inventoryTransactionMapper = inventoryTransactionMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Product adjustOne(Long productId, int delta) {
        if (delta == 0) throw new BizException("库存调整量不能为 0");
        UpdateWrapper<Product> uw = new UpdateWrapper<>();
        uw.eq("id", productId).setSql("available_stock = available_stock + " + delta).apply("available_stock + {0} >= 0", delta);
        if (productMapper.update(null, uw) == 0) {
            if (productMapper.selectById(productId) == null) throw new BizException("商品不存在");
            throw new BizException("库存扣减后不能为负数");
        }
        insertTransaction(productId, delta, "SUCCESS", null);
        return productMapper.selectById(productId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordFailedAdjustment(Long productId, int delta, String reason) {
        insertTransaction(productId, 0, "FAILED", "申请调整 " + delta + " 被拒绝：" + reason);
    }

    @Override
    public List<InventoryTransactionVO> recentLogs(Long productId, int limit) {
        List<InventoryTransaction> transactions = inventoryTransactionMapper.selectList(
                new QueryWrapper<InventoryTransaction>().eq("product_id", productId)
                        .orderByDesc("create_time").orderByDesc("id").last("LIMIT " + limit));
        List<InventoryTransactionVO> logs = new ArrayList<>();
        for (InventoryTransaction t : transactions) logs.add(toLogRow(t));
        return logs;
    }

    @Override
    public PageVO<InventoryTransactionVO> pageTransactions(int page, int size, Long productId, String bizType, String status) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        String bizTypeValue = (bizType != null && !bizType.trim().isEmpty()) ? bizType.trim() : null;
        String statusValue = (status != null && !status.trim().isEmpty()) ? status.trim() : null;
        long total = inventoryTransactionMapper.countTransactions(productId, bizTypeValue, statusValue);
        List<InventoryTransaction> rows = inventoryTransactionMapper.selectTransactionPage(
                productId, bizTypeValue, statusValue, safeSize, (long) (safePage - 1) * safeSize);
        List<InventoryTransactionVO> records = new ArrayList<>();
        for (InventoryTransaction t : rows) records.add(toLogRow(t));
        PageVO<InventoryTransactionVO> result = new PageVO<>();
        result.setTotal(total);
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(records);
        return result;
    }

    private InventoryTransactionVO toLogRow(InventoryTransaction t) {
        InventoryTransactionVO vo = new InventoryTransactionVO();
        vo.setId(t.getId());
        vo.setBizNo(t.getBizNo());
        vo.setOperationType(t.getBizType());
        vo.setOperationStatus(t.getOperationStatus() == null ? "SUCCESS" : t.getOperationStatus());
        vo.setOrderNo(t.getOrderNo());
        vo.setAvailableDelta(t.getAvailableDelta());
        vo.setLockedDelta(t.getLockedDelta());
        vo.setErrorMessage(t.getErrorMessage());
        vo.setCreateTime(t.getCreateTime());
        return vo;
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
        inventoryTransactionMapper.insert(transaction);
    }
}
