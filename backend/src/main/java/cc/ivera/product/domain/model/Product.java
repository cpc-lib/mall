package cc.ivera.product.domain.model;

import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import lombok.Data;

import java.util.Date;

/**
 * 商品聚合根：商品信息 + 库存四桶（available/locked/sold/lost）。
 *
 * <p>四桶恒等式：available + locked + sold + lost = 入库总量，任意流转总量守恒。
 * 本类的内存流转方法表达领域规则与前置守卫；并发场景的权威闸门仍是仓储内
 * SQL 条件 UPDATE（available>=qty 等，防超卖根闸门，见 ProductMapper.xml）。</p>
 */
@Data
public class Product {

    private Long id;

    private String title; //商品名称

    private Integer price; //价格（分）

    private Integer stock; //可用库存

    private Integer lockedStock; //锁定库存：下单预占未结转的数量（支付后保持锁定）

    private Integer soldStock; //已售库存：确认收货结转的数量（已售退款回补时扣减）

    private Integer lostStock; //丢失/货损库存：仅退款未收货核销的数量（货物不回仓）

    private String productStatus; //ENABLED / DISABLED

    private Date createTime;

    private Date updateTime;

    /**
     * 新建商品工厂：初始可用库存即入库量，其余三桶为 0，默认上架。
     */
    public static Product create(String title, Integer price, Integer stock) {
        Product product = new Product();
        product.setTitle(title);
        product.setPrice(price);
        product.setStock(stock);
        product.setLockedStock(0);
        product.setSoldStock(0);
        product.setLostStock(0);
        product.setProductStatus(CommonStatus.ENABLED.getType());
        return product;
    }

    /**
     * 上架/下架状态变更。
     */
    public void changeStatus(String productStatus, Date now) {
        this.productStatus = productStatus;
        this.updateTime = now;
    }

    /**
     * 下单预占：available-=qty, locked+=qty；可用不足拒绝。
     */
    public void reserve(int qty) {
        if (stock(stock) < qty) {
            throw new BizException("库存不足，productId=" + id);
        }
        mutate(-qty, qty, 0, 0);
    }

    /**
     * 关单/取消释放预占（退款未收货回补同一流转）：available+=qty, locked-=qty；锁定不足拒绝。
     */
    public void releaseReserved(int qty) {
        if (stock(lockedStock) < qty) {
            throw new BizException("库存预占释放失败，productId=" + id);
        }
        mutate(qty, -qty, 0, 0);
    }

    /**
     * 确认收货结转已售：locked-=qty, sold+=qty；锁定不足拒绝。
     */
    public void commitSold(int qty) {
        if (stock(lockedStock) < qty) {
            throw new BizException("确认收货结转失败（锁定库存不足），productId=" + id);
        }
        mutate(0, -qty, qty, 0);
    }

    /**
     * 已收货退款回补：sold-=qty, available+=qty；已售不足拒绝。
     */
    public void restockFromSold(int qty) {
        if (stock(soldStock) < qty) {
            throw new BizException("已售库存不足，无法回补，productId=" + id);
        }
        mutate(qty, 0, -qty, 0);
    }

    /**
     * 仅退款未收货核销货损：locked-=qty, lost+=qty；锁定不足拒绝。
     */
    public void writeOffLockedLost(int qty) {
        if (stock(lockedStock) < qty) {
            throw new BizException("锁定库存不足，无法核销货损，productId=" + id);
        }
        mutate(0, -qty, 0, qty);
    }

    /**
     * 仅退款已收货核销货损：sold-=qty, lost+=qty；已售不足拒绝。
     */
    public void writeOffSoldLost(int qty) {
        if (stock(soldStock) < qty) {
            throw new BizException("已售库存不足，无法核销货损，productId=" + id);
        }
        mutate(0, 0, -qty, qty);
    }

    /**
     * 管理员手工调整（补货为正、扣减为负）：调整量为 0 或扣减后为负拒绝。
     */
    public void adjust(int delta) {
        if (delta == 0) {
            throw new BizException("库存调整量不能为 0");
        }
        if (stock(stock) + delta < 0) {
            throw new BizException("库存扣减后不能为负数");
        }
        mutate(delta, 0, 0, 0);
    }

    /**
     * 四桶内存流转：四个增量之和恒为 0（总量守恒），流转后任一桶不得为负。
     */
    private void mutate(int availableDelta, int lockedDelta, int soldDelta, int lostDelta) {
        int available = stock(stock) + availableDelta;
        int locked = stock(lockedStock) + lockedDelta;
        int sold = stock(soldStock) + soldDelta;
        int lost = stock(lostStock) + lostDelta;
        if (available < 0 || locked < 0 || sold < 0 || lost < 0) {
            throw new BizException("库存变动后存在负桶，违反四桶恒等式，productId=" + id);
        }
        this.stock = available;
        this.lockedStock = locked;
        this.soldStock = sold;
        this.lostStock = lost;
    }

    private static int stock(Integer value) {
        return value == null ? 0 : value;
    }
}
