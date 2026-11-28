package cc.ivera.product.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_product_spec_value")
public class ProductSpecValuePO extends BaseEntity {

    private Long spuId;
    private Long groupId;
    private String specValue;
    private Integer sortOrder;
}
