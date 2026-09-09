package cc.ivera.product.domain.model;

import cc.ivera.shared.domain.exception.BizException;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * StockImport 导入批次聚合根状态机纯单元测试：PENDING → CONFIRMED，仅 PENDING 可编辑/确认。
 */
class StockImportAggregateTest {

    @Test
    void createPendingInitializesFields() {
        StockImport record = StockImport.createPending("a.xlsx", 2, "[{\"productId\":1,\"delta\":5}]");
        assertEquals("a.xlsx", record.getFileName());
        assertEquals(2, record.getItemCount());
        assertEquals(StockImport.STATUS_PENDING, record.getStatus());
        assertEquals("[{\"productId\":1,\"delta\":5}]", record.getItemsJson());
    }

    @Test
    void confirmTransitionsPendingToConfirmed() {
        StockImport record = StockImport.createPending("a.xlsx", 1, "[]");
        Date now = new Date();
        record.confirm(now);
        assertEquals(StockImport.STATUS_CONFIRMED, record.getStatus());
        assertEquals(now, record.getConfirmTime());
    }

    @Test
    void confirmRejectsNonPending() {
        StockImport record = StockImport.createPending("a.xlsx", 1, "[]");
        record.confirm(new Date());
        assertThrows(BizException.class, () -> record.confirm(new Date()));
    }

    @Test
    void requirePendingForEditPassesOnPendingAndRejectsAfterConfirm() {
        StockImport record = StockImport.createPending("a.xlsx", 1, "[]");
        record.requirePendingForEdit();
        record.confirm(new Date());
        assertThrows(BizException.class, record::requirePendingForEdit);
    }
}
