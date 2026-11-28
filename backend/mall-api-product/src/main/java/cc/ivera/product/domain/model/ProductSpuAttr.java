package cc.ivera.product.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 商品基础展示属性（SPU 参数，如品牌、产地、材质、讲师），不拆分 SKU。
 */
@Data
public class ProductSpuAttr {

    private Long id;
    private Long spuId;
    private String attrName;
    private String attrValue;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}
