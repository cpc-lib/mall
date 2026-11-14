package cc.ivera.api.product;

import cc.ivera.product.domain.model.Product;

/**
 * 商品只读契约：供其它上下文获取商品快照，不暴露商品仓储。
 * P0 阶段为进程内适配器实现；商品服务拆分后替换为远程调用。
 */
public interface ProductQueryApi {

    /**
     * 按商品主键查询，不存在返回 null。
     */
    Product findById(Long id);
}
