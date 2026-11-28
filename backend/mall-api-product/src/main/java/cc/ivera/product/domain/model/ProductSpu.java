package cc.ivera.product.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 标准商品单元（SPU）聚合根：承载商品公共展示信息、规格模式、价格区间与默认 SKU，
 * 不直接持有四桶物理库存。
 */
@Data
public class ProductSpu {

    public static final String SPEC_MODE_SINGLE = "SINGLE";
    public static final String SPEC_MODE_MULTI = "MULTI";

    private Long id;
    private String spuCode;
    private String title;
    private String subtitle;
    private String categoryName;
    private String brandName;
    private String mainImage;
    private String description;
    private String specMode;
    private String spuStatus;
    private Integer minPrice;
    private Integer maxPrice;
    private Long defaultSkuId;
    private Date createTime;
    private Date updateTime;
}
