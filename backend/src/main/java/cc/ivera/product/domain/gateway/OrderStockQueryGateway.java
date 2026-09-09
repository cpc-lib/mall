package cc.ivera.product.domain.gateway;

import cc.ivera.product.domain.model.OrderItemStockLine;

import java.util.List;

/**
 * 订单上下文出站端口（库存域视角）：库存动作需要读取订单成交行、
 * 累计订单明细的已回补/已核销数量。由订单上下文提供实现（ACL 防腐层）。
 */
public interface OrderStockQueryGateway {

    /**
     * 按订单号查询成交行（id 升序），供确认收货结转已售。
     */
    List<OrderItemStockLine> listItemsByOrderNo(String orderNo);

    /**
     * 订单明细补库/货损核销数量累计 CAS：restocked+qty 不超过 已退+冻结。
     *
     * @return 是否累计成功（false 表示超出上限）
     */
    boolean addRestockedQty(Long orderItemId, Integer qty);
}
