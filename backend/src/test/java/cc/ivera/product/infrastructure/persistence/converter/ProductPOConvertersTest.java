package cc.ivera.product.infrastructure.persistence.converter;

import cc.ivera.product.domain.model.InventoryReservation;
import cc.ivera.product.domain.model.InventoryTransaction;
import cc.ivera.product.domain.model.Product;
import cc.ivera.product.domain.model.StockImport;
import cc.ivera.product.infrastructure.persistence.po.InventoryReservationPO;
import cc.ivera.product.infrastructure.persistence.po.InventoryTransactionPO;
import cc.ivera.product.infrastructure.persistence.po.ProductPO;
import cc.ivera.product.infrastructure.persistence.po.StockImportPO;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * product 上下文 PO ↔ 领域对象 Converter 字段往返测试：不连数据库。
 */
class ProductPOConvertersTest {

    @Test
    void productRoundtrip() {
        Date now = new Date();
        Product domain = Product.create("商品", 199, 7);
        domain.setId(13L);
        domain.setLockedStock(3);
        domain.setSoldStock(2);
        domain.setLostStock(1);
        domain.setCreateTime(now);
        domain.setUpdateTime(now);

        ProductPO po = ProductPOConverter.toPO(domain);
        assertEquals(13L, po.getId());
        assertEquals("商品", po.getTitle());
        assertEquals(199, po.getPrice());
        assertEquals(7, po.getStock());
        assertEquals(3, po.getLockedStock());
        assertEquals(2, po.getSoldStock());
        assertEquals(1, po.getLostStock());
        assertEquals("ENABLED", po.getProductStatus());

        Product back = ProductPOConverter.toDomain(po);
        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getTitle(), back.getTitle());
        assertEquals(domain.getPrice(), back.getPrice());
        assertEquals(domain.getStock(), back.getStock());
        assertEquals(domain.getLockedStock(), back.getLockedStock());
        assertEquals(domain.getSoldStock(), back.getSoldStock());
        assertEquals(domain.getLostStock(), back.getLostStock());
        assertEquals(domain.getProductStatus(), back.getProductStatus());
        assertEquals(now, back.getCreateTime());

        assertNull(ProductPOConverter.toPO(null));
        assertNull(ProductPOConverter.toDomain(null));
    }

    @Test
    void stockImportRoundtrip() {
        Date now = new Date();
        StockImport domain = StockImport.createPending("b.xlsx", 1, "[]");
        domain.setId(21L);
        domain.setStorageType("LOCAL");
        domain.setFilePath("/tmp/21.xlsx");
        domain.setConfirmTime(now);
        domain.setCreateTime(now);
        domain.setUpdateTime(now);

        StockImportPO po = StockImportPOConverter.toPO(domain);
        assertEquals(21L, po.getId());
        assertEquals("b.xlsx", po.getFileName());
        assertEquals(1, po.getItemCount());
        assertEquals("PENDING", po.getStatus());
        assertEquals("[]", po.getItemsJson());
        assertEquals("LOCAL", po.getStorageType());
        assertEquals("/tmp/21.xlsx", po.getFilePath());
        assertEquals(now, po.getConfirmTime());

        StockImport back = StockImportPOConverter.toDomain(po);
        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getFileName(), back.getFileName());
        assertEquals(domain.getItemCount(), back.getItemCount());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getItemsJson(), back.getItemsJson());
        assertEquals(domain.getStorageType(), back.getStorageType());
        assertEquals(domain.getFilePath(), back.getFilePath());
        assertEquals(now, back.getConfirmTime());
    }

    @Test
    void inventoryTransactionRoundtrip() {
        Date now = new Date();
        InventoryTransaction domain = new InventoryTransaction();
        domain.setId(31L);
        domain.setBizNo("ORDER_RESERVE:NO1:9");
        domain.setBizType("ORDER_RESERVE");
        domain.setOrderNo("NO1");
        domain.setOrderItemId(9L);
        domain.setRefundNo(null);
        domain.setProductId(5L);
        domain.setAvailableDelta(-2);
        domain.setLockedDelta(2);
        domain.setSoldDelta(0);
        domain.setLostDelta(0);
        domain.setOperationStatus("SUCCESS");
        domain.setErrorMessage(null);
        domain.setCreateTime(now);
        domain.setUpdateTime(now);

        InventoryTransactionPO po = InventoryTransactionPOConverter.toPO(domain);
        assertEquals(31L, po.getId());
        assertEquals("ORDER_RESERVE:NO1:9", po.getBizNo());
        assertEquals(-2, po.getAvailableDelta());
        assertEquals(2, po.getLockedDelta());

        InventoryTransaction back = InventoryTransactionPOConverter.toDomain(po);
        assertEquals(domain.getBizNo(), back.getBizNo());
        assertEquals(domain.getBizType(), back.getBizType());
        assertEquals(domain.getOrderNo(), back.getOrderNo());
        assertEquals(domain.getOrderItemId(), back.getOrderItemId());
        assertEquals(domain.getRefundNo(), back.getRefundNo());
        assertEquals(domain.getProductId(), back.getProductId());
        assertEquals(domain.getAvailableDelta(), back.getAvailableDelta());
        assertEquals(domain.getLockedDelta(), back.getLockedDelta());
        assertEquals(domain.getSoldDelta(), back.getSoldDelta());
        assertEquals(domain.getLostDelta(), back.getLostDelta());
        assertEquals(domain.getOperationStatus(), back.getOperationStatus());
        assertEquals(domain.getErrorMessage(), back.getErrorMessage());
    }

    @Test
    void inventoryReservationRoundtrip() {
        Date now = new Date();
        InventoryReservation domain = new InventoryReservation();
        domain.setId(41L);
        domain.setReservationNo("RV1");
        domain.setOrderNo("NO1");
        domain.setOrderItemId(9L);
        domain.setProductId(5L);
        domain.setQuantity(2);
        domain.setStatus("LOCKED");
        domain.setExpireTime(now);
        domain.setCommitTime(null);
        domain.setReleaseTime(null);
        domain.setCreateTime(now);
        domain.setUpdateTime(now);

        InventoryReservationPO po = InventoryReservationPOConverter.toPO(domain);
        assertEquals(41L, po.getId());
        assertEquals("RV1", po.getReservationNo());
        assertEquals("NO1", po.getOrderNo());
        assertEquals(9L, po.getOrderItemId());
        assertEquals(5L, po.getProductId());
        assertEquals(2, po.getQuantity());
        assertEquals("LOCKED", po.getStatus());
        assertEquals(now, po.getExpireTime());

        InventoryReservation back = InventoryReservationPOConverter.toDomain(po);
        assertEquals(domain.getReservationNo(), back.getReservationNo());
        assertEquals(domain.getOrderNo(), back.getOrderNo());
        assertEquals(domain.getOrderItemId(), back.getOrderItemId());
        assertEquals(domain.getProductId(), back.getProductId());
        assertEquals(domain.getQuantity(), back.getQuantity());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getExpireTime(), back.getExpireTime());
        assertEquals(domain.getCommitTime(), back.getCommitTime());
        assertEquals(domain.getReleaseTime(), back.getReleaseTime());
    }
}
