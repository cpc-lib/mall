package cc.ivera.product.domain.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 商品销售规格组（如颜色、内存容量、课程版本）。
 */
@Data
public class ProductSpecGroup {

    private Long id;
    private Long spuId;
    private String specName;
    private Integer sortOrder;
    private List<ProductSpecValue> values = new ArrayList<>();
    private Date createTime;
    private Date updateTime;
}
