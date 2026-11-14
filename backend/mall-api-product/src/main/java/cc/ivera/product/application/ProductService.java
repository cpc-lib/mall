package cc.ivera.product.application;

import cc.ivera.product.domain.model.Product;

import java.util.List;

/**
 * 商品应用服务：商品列表/详情/新增/上下架。
 */
public interface ProductService {

    /**
     * 商品列表（按 id 升序）。
     */
    List<Product> list();

    /**
     * 商品详情，不存在抛 BizException。
     */
    Product getById(Long id);

    /**
     * 新增商品：校验名称/价格/库存，初始可用库存即入库量，默认上架。
     */
    Product create(String title, Integer price, Integer stock);

    /**
     * 上架/下架：状态仅支持 ENABLED / DISABLED，商品不存在抛 BizException。
     */
    Product changeStatus(Long id, String productStatus);
}
