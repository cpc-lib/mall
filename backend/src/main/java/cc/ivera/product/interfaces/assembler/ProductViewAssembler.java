package cc.ivera.product.interfaces.assembler;

import cc.ivera.product.application.dto.StockAdjustResult;
import cc.ivera.product.domain.model.InventoryTransaction;
import cc.ivera.product.domain.model.StockAdjustLine;
import cc.ivera.product.domain.model.StockImport;
import cc.ivera.product.interfaces.dto.ProductStockBatchAdjustRequest;
import cc.ivera.product.interfaces.vo.BatchAdjustItemVO;
import cc.ivera.product.interfaces.vo.BatchAdjustResultVO;
import cc.ivera.product.interfaces.vo.InventoryTransactionVO;
import cc.ivera.product.interfaces.vo.StockImportVO;
import cc.ivera.shared.web.PageVO;

import java.util.ArrayList;
import java.util.List;

/**
 * 商品/库存上下文 interfaces 层装配器：领域对象/应用结果 → 对外 VO，请求明细 → 领域行类型。
 * 字段映射与原 Service 内 toLogRow/toRow 行为一致（operationStatus 为空兜底 SUCCESS）。
 */
public final class ProductViewAssembler {

    private ProductViewAssembler() {
    }

    public static InventoryTransactionVO toTransactionVO(InventoryTransaction t) {
        InventoryTransactionVO vo = new InventoryTransactionVO();
        vo.setId(t.getId());
        vo.setBizNo(t.getBizNo());
        vo.setOperationType(t.getBizType());
        vo.setOperationStatus(t.getOperationStatus() == null ? "SUCCESS" : t.getOperationStatus());
        vo.setOrderNo(t.getOrderNo());
        vo.setRefundNo(t.getRefundNo());
        vo.setProductId(t.getProductId());
        vo.setAvailableDelta(t.getAvailableDelta());
        vo.setLockedDelta(t.getLockedDelta());
        vo.setSoldDelta(t.getSoldDelta());
        vo.setLostDelta(t.getLostDelta());
        vo.setErrorMessage(t.getErrorMessage());
        vo.setCreateTime(t.getCreateTime());
        return vo;
    }

    public static List<InventoryTransactionVO> toTransactionVOList(List<InventoryTransaction> transactions) {
        List<InventoryTransactionVO> logs = new ArrayList<>();
        for (InventoryTransaction t : transactions) logs.add(toTransactionVO(t));
        return logs;
    }

    public static PageVO<InventoryTransactionVO> toTransactionPage(PageVO<InventoryTransaction> page) {
        PageVO<InventoryTransactionVO> result = new PageVO<>();
        result.setTotal(page.getTotal());
        result.setPage(page.getPage());
        result.setSize(page.getSize());
        result.setRecords(toTransactionVOList(page.getRecords()));
        return result;
    }

    public static StockImportVO toStockImportVO(StockImport record) {
        StockImportVO vo = new StockImportVO();
        vo.setId(record.getId());
        vo.setFileName(record.getFileName());
        vo.setItemCount(record.getItemCount());
        vo.setStatus(record.getStatus());
        vo.setItems(record.getItems());
        vo.setCreateTime(record.getCreateTime());
        vo.setConfirmTime(record.getConfirmTime());
        return vo;
    }

    public static List<StockImportVO> toStockImportVOList(List<StockImport> records) {
        List<StockImportVO> result = new ArrayList<>();
        for (StockImport record : records) result.add(toStockImportVO(record));
        return result;
    }

    public static BatchAdjustResultVO toBatchAdjustResultVO(StockAdjustResult body) {
        List<BatchAdjustItemVO> results = new ArrayList<>();
        for (StockAdjustResult.Row row : body.getRows()) {
            BatchAdjustItemVO vo = new BatchAdjustItemVO();
            vo.setProductId(row.getProductId());
            vo.setDelta(row.getDelta());
            vo.setSuccess(row.getSuccess());
            vo.setStock(row.getStock());
            vo.setMessage(row.getMessage());
            results.add(vo);
        }
        BatchAdjustResultVO vo = new BatchAdjustResultVO();
        vo.setTotal(body.getTotal());
        vo.setSuccessCount(body.getSuccessCount());
        vo.setFailCount(body.getFailCount());
        vo.setResults(results);
        return vo;
    }

    public static List<StockAdjustLine> toAdjustLines(List<ProductStockBatchAdjustRequest.Item> items) {
        List<StockAdjustLine> lines = new ArrayList<>();
        if (items != null) {
            for (ProductStockBatchAdjustRequest.Item item : items) {
                lines.add(new StockAdjustLine(item.getProductId(), item.getDelta()));
            }
        }
        return lines;
    }
}
