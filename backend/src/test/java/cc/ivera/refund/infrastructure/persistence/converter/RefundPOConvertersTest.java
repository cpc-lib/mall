package cc.ivera.refund.infrastructure.persistence.converter;

import cc.ivera.refund.domain.model.RefundInfo;
import cc.ivera.refund.domain.model.RefundItem;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.refund.infrastructure.persistence.po.RefundInfoPO;
import cc.ivera.refund.infrastructure.persistence.po.RefundItemPO;
import cc.ivera.refund.infrastructure.persistence.po.RefundOrderPO;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * refund 上下文 PO ↔ 领域对象手写 Converter 往返映射测试：全字段等值，不连数据库。
 */
class RefundPOConvertersTest {

    @Test
    void refundOrderRoundtripMapsAllFields() {
        RefundOrder domain = new RefundOrder();
        domain.setId(1L);
        domain.setRefundNo("RF1");
        domain.setOrderNo("ORD1");
        domain.setUserId(7L);
        domain.setRefundType("CANCEL_BEFORE_SHIP");
        domain.setPaymentNo("PAY1");
        domain.setRefundAmount(9900);
        domain.setReason("不想要了");
        domain.setStatus("APPLYING");
        domain.setLegacyApplyStatus("PENDING");
        domain.setApplyType("USER");
        domain.setAdminRemark("自动受理");
        domain.setAcceptedTime(new Date(1_000L));
        domain.setSuccessTime(new Date(2_000L));
        domain.setCreateTime(new Date(3_000L));
        domain.setUpdateTime(new Date(4_000L));

        RefundOrder back = RefundOrderPOConverter.toDomain(RefundOrderPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getRefundNo(), back.getRefundNo());
        assertEquals(domain.getOrderNo(), back.getOrderNo());
        assertEquals(domain.getUserId(), back.getUserId());
        assertEquals(domain.getRefundType(), back.getRefundType());
        assertEquals(domain.getPaymentNo(), back.getPaymentNo());
        assertEquals(domain.getRefundAmount(), back.getRefundAmount());
        assertEquals(domain.getReason(), back.getReason());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getLegacyApplyStatus(), back.getLegacyApplyStatus());
        assertEquals(domain.getApplyType(), back.getApplyType());
        assertEquals(domain.getAdminRemark(), back.getAdminRemark());
        assertEquals(domain.getAcceptedTime(), back.getAcceptedTime());
        assertEquals(domain.getSuccessTime(), back.getSuccessTime());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void refundOrderConverterNullSafe() {
        assertNull(RefundOrderPOConverter.toPO(null));
        assertNull(RefundOrderPOConverter.toDomain((RefundOrderPO) null));
    }

    @Test
    void refundItemRoundtripMapsAllFields() {
        RefundItem domain = new RefundItem();
        domain.setId(2L);
        domain.setRefundOrderId(1L);
        domain.setRefundNo("RF1");
        domain.setOrderItemId(22L);
        domain.setProductId(3L);
        domain.setUnitPrice(1500);
        domain.setRefundQty(2);
        domain.setRefundAmount(3000);
        domain.setRestockQty(1);
        domain.setLegacyStockReturned(0);
        domain.setStatus("APPLYING");
        domain.setCreateTime(new Date(5_000L));
        domain.setUpdateTime(new Date(6_000L));

        RefundItem back = RefundItemPOConverter.toDomain(RefundItemPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getRefundOrderId(), back.getRefundOrderId());
        assertEquals(domain.getRefundNo(), back.getRefundNo());
        assertEquals(domain.getOrderItemId(), back.getOrderItemId());
        assertEquals(domain.getProductId(), back.getProductId());
        assertEquals(domain.getUnitPrice(), back.getUnitPrice());
        assertEquals(domain.getRefundQty(), back.getRefundQty());
        assertEquals(domain.getRefundAmount(), back.getRefundAmount());
        assertEquals(domain.getRestockQty(), back.getRestockQty());
        assertEquals(domain.getLegacyStockReturned(), back.getLegacyStockReturned());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void refundItemConverterNullSafe() {
        assertNull(RefundItemPOConverter.toPO(null));
        assertNull(RefundItemPOConverter.toDomain((RefundItemPO) null));
    }

    @Test
    void refundInfoRoundtripMapsAllFields() {
        RefundInfo domain = new RefundInfo();
        domain.setId(3L);
        domain.setOrderNo("ORD1");
        domain.setRefundNo("RF1");
        domain.setRefundId("WX-REFUND-1");
        domain.setTotalFee(9900);
        domain.setRefund(9900);
        domain.setReason("质量问题");
        domain.setApprovalStatus("APPROVED");
        domain.setApproveRemark("同意");
        domain.setApprovedTime(new Date(7_000L));
        domain.setRefundStatus("SUCCESS");
        domain.setContentReturn("{\"code\":\"OK\"}");
        domain.setContentNotify("{\"refund_status\":\"SUCCESS\"}");
        domain.setCreateTime(new Date(8_000L));
        domain.setUpdateTime(new Date(9_000L));

        RefundInfo back = RefundInfoPOConverter.toDomain(RefundInfoPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getOrderNo(), back.getOrderNo());
        assertEquals(domain.getRefundNo(), back.getRefundNo());
        assertEquals(domain.getRefundId(), back.getRefundId());
        assertEquals(domain.getTotalFee(), back.getTotalFee());
        assertEquals(domain.getRefund(), back.getRefund());
        assertEquals(domain.getReason(), back.getReason());
        assertEquals(domain.getApprovalStatus(), back.getApprovalStatus());
        assertEquals(domain.getApproveRemark(), back.getApproveRemark());
        assertEquals(domain.getApprovedTime(), back.getApprovedTime());
        assertEquals(domain.getRefundStatus(), back.getRefundStatus());
        assertEquals(domain.getContentReturn(), back.getContentReturn());
        assertEquals(domain.getContentNotify(), back.getContentNotify());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void refundInfoConverterNullSafe() {
        assertNull(RefundInfoPOConverter.toPO(null));
        assertNull(RefundInfoPOConverter.toDomain((RefundInfoPO) null));
    }
}
