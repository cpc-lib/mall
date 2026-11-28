package cc.ivera.product.application.vo;

import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.ProductSpecGroup;
import cc.ivera.product.domain.model.ProductSpu;
import cc.ivera.product.domain.model.ProductSpuAttr;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * SPU 完整详情 VO：包含 SPU 基本信息、基础展示属性、销售规格树与下属 SKU 列表。
 */
@Data
public class SpuDetailVO {

    private ProductSpu spu;
    private List<ProductSpuAttr> attrs = new ArrayList<>();
    private List<ProductSpecGroup> specGroups = new ArrayList<>();
    private List<Product> skus = new ArrayList<>();
}
