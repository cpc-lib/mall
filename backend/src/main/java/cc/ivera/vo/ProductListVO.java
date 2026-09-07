package cc.ivera.vo;

import cc.ivera.entity.Product;
import lombok.Data;

import java.util.List;

/**
 * 商品列表响应 VO。
 */
@Data
public class ProductListVO {

    private List<Product> productList;
}
