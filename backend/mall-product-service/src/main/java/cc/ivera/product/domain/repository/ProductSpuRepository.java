package cc.ivera.product.domain.repository;

import cc.ivera.product.domain.model.ProductSpecGroup;
import cc.ivera.product.domain.model.ProductSpu;
import cc.ivera.product.domain.model.ProductSpuAttr;

import java.util.Collection;
import java.util.List;

/**
 * SPU、基础展示属性与销售规格仓储端口。
 */
public interface ProductSpuRepository {

    ProductSpu findById(Long id);

    List<ProductSpu> findByIds(Collection<Long> ids);

    long countSpus(String keyword, boolean onlyEnabled);

    List<ProductSpu> findSpuPage(String keyword, boolean onlyEnabled, int limit, long offset);

    void insertSpu(ProductSpu spu);

    void updateSpu(ProductSpu spu);

    List<ProductSpuAttr> listAttrsBySpuId(Long spuId);

    List<ProductSpuAttr> listAttrsBySpuIds(Collection<Long> spuIds);

    void replaceAttrs(Long spuId, List<ProductSpuAttr> attrs);

    List<ProductSpecGroup> listSpecGroupsWithValues(Long spuId);

    /**
     * 同步销售规格组与规格值（增量保留同名组/值 ID，新增缺失项，使已有 SKU 的 spec_key 尽量稳定）。
     */
    List<ProductSpecGroup> syncSpecGroupsAndValues(Long spuId, List<ProductSpecGroup> desiredGroups);
}
