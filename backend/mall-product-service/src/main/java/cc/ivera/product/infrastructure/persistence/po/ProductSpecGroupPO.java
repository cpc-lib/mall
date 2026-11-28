package cc.ivera.product.infrastructure.persistence.po;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("t_product_spec_group")
public class ProductSpecGroupPO extends BaseEntity {

    private Long spuId;
    private String specName;
    private Integer sortOrder;
}
