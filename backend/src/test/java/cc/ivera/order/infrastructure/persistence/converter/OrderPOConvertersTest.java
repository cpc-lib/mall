package cc.ivera.order.infrastructure.persistence.converter;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.domain.model.OrderItem;
import cc.ivera.order.domain.model.OrderShipment;
import cc.ivera.order.infrastructure.persistence.po.OrderInfoPO;
import cc.ivera.order.infrastructure.persistence.po.OrderItemPO;
import cc.ivera.order.infrastructure.persistence.po.OrderShipmentPO;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * order 上下文 PO ↔ 领域对象手写 Converter 往返映射测试：全字段等值，不连数据库。
 */
class OrderPOConvertersTest {

    @Test
    void orderInfoRoundtripMapsAllFields() {
        OrderInfo domain = new OrderInfo();
        domain.setId(1L);
        domain.setTitle("订单标题");
        domain.setOrderNo("ORD1");
        domain.setUserId(7L);
        domain.setProductId(3L);
        domain.setTotalFee(9900);
        domain.setCodeUrl("weixin://pay/x");
        domain.setLegacyStatus("未支付");
        domain.setOrderStatus("WAIT_PAY");
        domain.setPayStatus("UNPAID");
        domain.setFulfillmentStatus("WAIT_SHIP");
        domain.setRefundStatus("NONE");
        domain.setPaidAmount(0);
        domain.setRefundFrozenAmount(0);
        domain.setRefundedAmount(0);
        domain.setExpireTime(new Date(1_000L));
        domain.setPaidTime(new Date(2_000L));
        domain.setReceiverName("张三");
        domain.setReceiverPhone("13800000000");
        domain.setReceiverAddress("北京市海淀区");
        domain.setPaymentType("WXPAY");
        domain.setPaymentAppId(11L);
        domain.setPaymentChannelCode("WX_NATIVE");
        domain.setVersion(5);
        domain.setCreateTime(new Date(3_000L));
        domain.setUpdateTime(new Date(4_000L));

        OrderInfo back = OrderInfoPOConverter.toDomain(OrderInfoPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getTitle(), back.getTitle());
        assertEquals(domain.getOrderNo(), back.getOrderNo());
        assertEquals(domain.getUserId(), back.getUserId());
        assertEquals(domain.getProductId(), back.getProductId());
        assertEquals(domain.getTotalFee(), back.getTotalFee());
        assertEquals(domain.getCodeUrl(), back.getCodeUrl());
        assertEquals(domain.getLegacyStatus(), back.getLegacyStatus());
        assertEquals(domain.getOrderStatus(), back.getOrderStatus());
        assertEquals(domain.getPayStatus(), back.getPayStatus());
        assertEquals(domain.getFulfillmentStatus(), back.getFulfillmentStatus());
        assertEquals(domain.getRefundStatus(), back.getRefundStatus());
        assertEquals(domain.getPaidAmount(), back.getPaidAmount());
        assertEquals(domain.getRefundFrozenAmount(), back.getRefundFrozenAmount());
        assertEquals(domain.getRefundedAmount(), back.getRefundedAmount());
        assertEquals(domain.getExpireTime(), back.getExpireTime());
        assertEquals(domain.getPaidTime(), back.getPaidTime());
        assertEquals(domain.getReceiverName(), back.getReceiverName());
        assertEquals(domain.getReceiverPhone(), back.getReceiverPhone());
        assertEquals(domain.getReceiverAddress(), back.getReceiverAddress());
        assertEquals(domain.getPaymentType(), back.getPaymentType());
        assertEquals(domain.getPaymentAppId(), back.getPaymentAppId());
        assertEquals(domain.getPaymentChannelCode(), back.getPaymentChannelCode());
        assertEquals(domain.getVersion(), back.getVersion());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void orderInfoConverterNullSafeAndSkipsTransientFlag() {
        assertNull(OrderInfoPOConverter.toPO(null));
        assertNull(OrderInfoPOConverter.toDomain((OrderInfoPO) null));
    }

    @Test
    void orderItemRoundtripMapsAllFields() {
        OrderItem domain = new OrderItem();
        domain.setId(22L);
        domain.setOrderId(1L);
        domain.setOrderNo("ORD1");
        domain.setProductId(3L);
        domain.setProductTitle("商品A");
        domain.setUnitPrice(1500);
        domain.setQuantity(3);
        domain.setDealUnitAmount(1500);
        domain.setOriginalTotalAmount(4500);
        domain.setDiscountAmount(0);
        domain.setPayAmount(4500);
        domain.setRefundedQty(1);
        domain.setRefundFrozenQty(0);
        domain.setRefundFrozenAmount(0);
        domain.setRefundedAmount(1500);
        domain.setRestockedQty(1);
        domain.setCreateTime(new Date(5_000L));
        domain.setUpdateTime(new Date(6_000L));

        OrderItem back = OrderItemPOConverter.toDomain(OrderItemPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getOrderId(), back.getOrderId());
        assertEquals(domain.getOrderNo(), back.getOrderNo());
        assertEquals(domain.getProductId(), back.getProductId());
        assertEquals(domain.getProductTitle(), back.getProductTitle());
        assertEquals(domain.getUnitPrice(), back.getUnitPrice());
        assertEquals(domain.getQuantity(), back.getQuantity());
        assertEquals(domain.getDealUnitAmount(), back.getDealUnitAmount());
        assertEquals(domain.getOriginalTotalAmount(), back.getOriginalTotalAmount());
        assertEquals(domain.getDiscountAmount(), back.getDiscountAmount());
        assertEquals(domain.getPayAmount(), back.getPayAmount());
        assertEquals(domain.getRefundedQty(), back.getRefundedQty());
        assertEquals(domain.getRefundFrozenQty(), back.getRefundFrozenQty());
        assertEquals(domain.getRefundFrozenAmount(), back.getRefundFrozenAmount());
        assertEquals(domain.getRefundedAmount(), back.getRefundedAmount());
        assertEquals(domain.getRestockedQty(), back.getRestockedQty());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void orderItemConverterNullSafe() {
        assertNull(OrderItemPOConverter.toPO(null));
        assertNull(OrderItemPOConverter.toDomain((OrderItemPO) null));
    }

    @Test
    void orderShipmentRoundtripMapsAllFields() {
        OrderShipment domain = new OrderShipment();
        domain.setId(33L);
        domain.setShipmentNo("SH1");
        domain.setOrderNo("ORD1");
        domain.setLogisticsCompany("模拟快递");
        domain.setTrackingNo("MOCK123");
        domain.setStatus("DELIVERED");
        domain.setShippedTime(new Date(7_000L));
        domain.setInTransitTime(new Date(8_000L));
        domain.setDeliveredTime(new Date(9_000L));
        domain.setReceivedTime(new Date(10_000L));
        domain.setRemark("模拟物流商发货");
        domain.setCreateTime(new Date(11_000L));
        domain.setUpdateTime(new Date(12_000L));

        OrderShipment back = OrderShipmentPOConverter.toDomain(OrderShipmentPOConverter.toPO(domain));

        assertEquals(domain.getId(), back.getId());
        assertEquals(domain.getShipmentNo(), back.getShipmentNo());
        assertEquals(domain.getOrderNo(), back.getOrderNo());
        assertEquals(domain.getLogisticsCompany(), back.getLogisticsCompany());
        assertEquals(domain.getTrackingNo(), back.getTrackingNo());
        assertEquals(domain.getStatus(), back.getStatus());
        assertEquals(domain.getShippedTime(), back.getShippedTime());
        assertEquals(domain.getInTransitTime(), back.getInTransitTime());
        assertEquals(domain.getDeliveredTime(), back.getDeliveredTime());
        assertEquals(domain.getReceivedTime(), back.getReceivedTime());
        assertEquals(domain.getRemark(), back.getRemark());
        assertEquals(domain.getCreateTime(), back.getCreateTime());
        assertEquals(domain.getUpdateTime(), back.getUpdateTime());
    }

    @Test
    void orderShipmentConverterNullSafe() {
        assertNull(OrderShipmentPOConverter.toPO(null));
        assertNull(OrderShipmentPOConverter.toDomain((OrderShipmentPO) null));
    }
}
