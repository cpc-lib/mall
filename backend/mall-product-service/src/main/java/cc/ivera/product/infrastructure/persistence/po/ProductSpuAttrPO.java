package cc.ivera.product.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_product_spu_attr")
public class ProductSpuAttrPO extends BaseEntity {

    private Long spuId;
    private String attrName;
    private String attrValue;
    private Integer sortOrder;
}
