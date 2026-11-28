package cc.ivera.product.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_product_spu")
public class ProductSpuPO extends BaseEntity {

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
}
