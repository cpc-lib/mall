package cc.ivera.product.interfaces.vo;

import cc.ivera.product.domain.model.Product;
import lombok.Data;

import java.util.List;

/**
 * 商品列表响应 VO。
 */
@Data
public class ProductListVO {

    private List<Product> productList;
}
