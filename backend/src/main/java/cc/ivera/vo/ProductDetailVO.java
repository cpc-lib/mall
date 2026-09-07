package cc.ivera.vo;

import cc.ivera.entity.Product;
import lombok.Data;

import java.util.List;

/**
 * 管理员商品详情 VO（商品 + 最近库存流水）。
 */
@Data
public class ProductDetailVO {

    private Product product;

    private List<InventoryTransactionVO> logs;
}
