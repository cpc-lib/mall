package cc.ivera.product.infrastructure.persistence.converter;

import cc.ivera.product.domain.model.Product;
import cc.ivera.product.infrastructure.persistence.po.ProductPO;

/**
 * Product 聚合根 ↔ ProductPO 转换（字段同名手写映射）。
 */
public final class ProductPOConverter {

    private ProductPOConverter() {
    }

    public static ProductPO toPO(Product domain) {
        if (domain == null) {
            return null;
        }
        ProductPO po = new ProductPO();
        po.setId(domain.getId());
        po.setSpuId(domain.getSpuId());
        po.setSkuCode(domain.getSkuCode());
        po.setTitle(domain.getTitle());
        po.setSpecKey(domain.getSpecKey());
        po.setSpecText(domain.getSpecText());
        po.setSpecJson(domain.getSpecJson());
        po.setIsDefault(Boolean.TRUE.equals(domain.getIsDefault()) ? "1" : "0");
        po.setPrice(domain.getPrice());
        po.setStock(domain.getStock());
        po.setLockedStock(domain.getLockedStock());
        po.setSoldStock(domain.getSoldStock());
        po.setLostStock(domain.getLostStock());
        po.setProductStatus(domain.getProductStatus());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static Product toDomain(ProductPO po) {
        if (po == null) {
            return null;
        }
        Product domain = new Product();
        domain.setId(po.getId());
        domain.setSpuId(po.getSpuId());
        domain.setSkuCode(po.getSkuCode());
        domain.setTitle(po.getTitle());
        domain.setSpecKey(po.getSpecKey());
        domain.setSpecText(po.getSpecText());
        domain.setSpecJson(po.getSpecJson());
        domain.setIsDefault("1".equals(po.getIsDefault()));
        domain.setPrice(po.getPrice());
        domain.setStock(po.getStock());
        domain.setLockedStock(po.getLockedStock());
        domain.setSoldStock(po.getSoldStock());
        domain.setLostStock(po.getLostStock());
        domain.setProductStatus(po.getProductStatus());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
