package cc.ivera.product.infrastructure.persistence.converter;

import cc.ivera.product.domain.model.StockImport;
import cc.ivera.product.infrastructure.persistence.po.StockImportPO;

/**
 * StockImport 聚合根 ↔ StockImportPO 转换。
 * 注意：领域对象的 items（明细快照解析结果）为非持久化字段，PO 无对应列，不参与映射。
 */
public final class StockImportPOConverter {

    private StockImportPOConverter() {
    }

    public static StockImportPO toPO(StockImport domain) {
        if (domain == null) {
            return null;
        }
        StockImportPO po = new StockImportPO();
        po.setId(domain.getId());
        po.setFileName(domain.getFileName());
        po.setItemCount(domain.getItemCount());
        po.setStatus(domain.getStatus());
        po.setItemsJson(domain.getItemsJson());
        po.setStorageType(domain.getStorageType());
        po.setFilePath(domain.getFilePath());
        po.setConfirmTime(domain.getConfirmTime());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static StockImport toDomain(StockImportPO po) {
        if (po == null) {
            return null;
        }
        StockImport domain = new StockImport();
        domain.setId(po.getId());
        domain.setFileName(po.getFileName());
        domain.setItemCount(po.getItemCount());
        domain.setStatus(po.getStatus());
        domain.setItemsJson(po.getItemsJson());
        domain.setStorageType(po.getStorageType());
        domain.setFilePath(po.getFilePath());
        domain.setConfirmTime(po.getConfirmTime());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
