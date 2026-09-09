package cc.ivera.product.domain.model;

import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Product 聚合根四桶库存流转纯单元测试：不连数据库/Redis/MQ。
 * 锁定的行为：available+locked+sold+lost 总量守恒（除入库 adjust 外），任一桶不得为负。
 */
class ProductAggregateTest {

    private int total(Product p) {
        return p.getStock() + p.getLockedStock() + p.getSoldStock() + p.getLostStock();
    }

    @Test
    void createInitializesBucketsAndEnabledStatus() {
        Product p = Product.create("商品", 100, 10);
        assertEquals(10, p.getStock());
        assertEquals(0, p.getLockedStock());
        assertEquals(0, p.getSoldStock());
        assertEquals(0, p.getLostStock());
        assertEquals(CommonStatus.ENABLED.getType(), p.getProductStatus());
    }

    @Test
    void reserveMovesAvailableToLocked() {
        Product p = Product.create("商品", 100, 10);
        p.reserve(4);
        assertEquals(6, p.getStock());
        assertEquals(4, p.getLockedStock());
        assertEquals(10, total(p));
    }

    @Test
    void reserveRejectsWhenAvailableInsufficient() {
        Product p = Product.create("商品", 100, 3);
        assertThrows(BizException.class, () -> p.reserve(5));
    }

    @Test
    void releaseReservedMovesLockedBackToAvailable() {
        Product p = Product.create("商品", 100, 10);
        p.reserve(4);
        p.releaseReserved(4);
        assertEquals(10, p.getStock());
        assertEquals(0, p.getLockedStock());
        assertEquals(10, total(p));
    }

    @Test
    void releaseReservedRejectsWhenLockedInsufficient() {
        Product p = Product.create("商品", 100, 10);
        p.reserve(3);
        assertThrows(BizException.class, () -> p.releaseReserved(4));
    }

    @Test
    void commitSoldMovesLockedToSold() {
        Product p = Product.create("商品", 100, 10);
        p.reserve(6);
        p.commitSold(6);
        assertEquals(4, p.getStock());
        assertEquals(0, p.getLockedStock());
        assertEquals(6, p.getSoldStock());
        assertEquals(10, total(p));
        assertThrows(BizException.class, () -> p.commitSold(1));
    }

    @Test
    void restockFromSoldMovesSoldBackToAvailable() {
        Product p = Product.create("商品", 100, 10);
        p.reserve(6);
        p.commitSold(6);
        p.restockFromSold(2);
        assertEquals(6, p.getStock());
        assertEquals(4, p.getSoldStock());
        assertEquals(10, total(p));
        assertThrows(BizException.class, () -> p.restockFromSold(5));
    }

    @Test
    void writeOffLockedLostMovesLockedToLost() {
        Product p = Product.create("商品", 100, 10);
        p.reserve(5);
        p.writeOffLockedLost(5);
        assertEquals(5, p.getStock());
        assertEquals(0, p.getLockedStock());
        assertEquals(5, p.getLostStock());
        assertEquals(10, total(p));
        assertThrows(BizException.class, () -> p.writeOffLockedLost(1));
    }

    @Test
    void writeOffSoldLostMovesSoldToLost() {
        Product p = Product.create("商品", 100, 10);
        p.reserve(6);
        p.commitSold(6);
        p.writeOffSoldLost(3);
        assertEquals(4, p.getStock());
        assertEquals(3, p.getSoldStock());
        assertEquals(3, p.getLostStock());
        assertEquals(10, total(p));
        assertThrows(BizException.class, () -> p.writeOffSoldLost(4));
    }

    @Test
    void fullRefundFlowKeepsIdentity() {
        // 下单10 → 释放4 → 结转6 → 已售回补2 → 已售核销3 → 再下单5 → 锁定核销5
        Product p = Product.create("商品", 100, 100);
        p.reserve(10);
        p.releaseReserved(4);
        p.commitSold(6);
        p.restockFromSold(2);
        p.writeOffSoldLost(3);
        p.reserve(5);
        p.writeOffLockedLost(5);
        assertEquals(91, p.getStock());
        assertEquals(0, p.getLockedStock());
        assertEquals(1, p.getSoldStock());
        assertEquals(8, p.getLostStock());
        assertEquals(100, total(p));
    }

    @Test
    void adjustPositiveAddsAvailable() {
        Product p = Product.create("商品", 100, 10);
        p.adjust(5);
        assertEquals(15, p.getStock());
        assertEquals(15, total(p));
    }

    @Test
    void adjustNegativeDeductsAvailable() {
        Product p = Product.create("商品", 100, 10);
        p.adjust(-3);
        assertEquals(7, p.getStock());
    }

    @Test
    void adjustRejectsZeroAndNegativeResult() {
        Product p = Product.create("商品", 100, 10);
        assertThrows(BizException.class, () -> p.adjust(0));
        assertThrows(BizException.class, () -> p.adjust(-11));
    }

    @Test
    void changeStatusUpdatesStatusAndTime() {
        Product p = Product.create("商品", 100, 10);
        java.util.Date now = new java.util.Date();
        p.changeStatus(CommonStatus.DISABLED.getType(), now);
        assertEquals(CommonStatus.DISABLED.getType(), p.getProductStatus());
        assertEquals(now, p.getUpdateTime());
    }
}
