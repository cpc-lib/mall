package cc.ivera.product.domain.repository;

import cc.ivera.product.domain.model.Product;

import java.util.List;

/**
 * 商品聚合仓储。
 * 库存四桶的并发权威闸门为条件 UPDATE（返回 0 表示前置条件不满足），
 * 由本仓储接口暴露、ProductMapper.xml 实现，SQL 一行不改。
 */
public interface ProductRepository {

    Product findById(Long id);

    List<Product> findAll();

    void insert(Product product);

    void update(Product product);

    /**
     * 管理员手工调整可用库存：available_stock = available_stock + delta，
     * 且调整后不得为负；条件不满足（商品不存在/扣减后为负）返回 false。
     */
    boolean adjustAvailable(Long id, int delta);

    /**
     * 下单预占：available-=qty, locked+=qty WHERE available>=qty（防超卖根闸门）。
     */
    int reserveStock(Long id, int quantity);

    /**
     * 释放预占：available+=qty, locked-=qty WHERE locked>=qty。
     */
    int releaseReservedStock(Long id, int quantity);

    /**
     * 确认收货结转已售：locked-=qty, sold+=qty WHERE locked>=qty。
     */
    int commitSoldStock(Long id, int quantity);

    /**
     * 已售退款回补：available+=qty, sold-=qty WHERE sold>=qty。
     */
    int releaseSoldStock(Long id, int quantity);

    /**
     * 仅退款未收货核销货损：locked-=qty, lost+=qty WHERE locked>=qty。
     */
    int writeOffLostStock(Long id, int quantity);

    /**
     * 仅退款已收货核销货损：sold-=qty, lost+=qty WHERE sold>=qty。
     */
    int writeOffSoldLostStock(Long id, int quantity);
}
