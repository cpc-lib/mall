package cc.ivera.product.domain.model;

import lombok.Data;

import java.util.Date;

/**
 * 商品销售规格可选值（如黑色、256GB、旗舰版）。
 */
@Data
public class ProductSpecValue {

    private Long id;
    private Long spuId;
    private Long groupId;
    private String specValue;
    private Integer sortOrder;
    private Date createTime;
    private Date updateTime;
}
