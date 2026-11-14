package cc.ivera.order.infrastructure.persistence.converter;

import cc.ivera.order.domain.model.OrderInfo;
import cc.ivera.order.infrastructure.persistence.po.OrderInfoPO;

/**
 * OrderInfo 聚合根 ↔ OrderInfoPO 转换（字段同名手写映射）。
 * 注意：领域补丁对象上的 applyPaidAmountFromTotalFee 为非持久化瞬态标记，不参与映射。
 */
public final class OrderInfoPOConverter {

    private OrderInfoPOConverter() {
    }

    public static OrderInfoPO toPO(OrderInfo domain) {
        if (domain == null) {
            return null;
        }
        OrderInfoPO po = new OrderInfoPO();
        po.setId(domain.getId());
        po.setTitle(domain.getTitle());
        po.setOrderNo(domain.getOrderNo());
        po.setUserId(domain.getUserId());
        po.setProductId(domain.getProductId());
        po.setTotalFee(domain.getTotalFee());
        po.setCodeUrl(domain.getCodeUrl());
        po.setLegacyStatus(domain.getLegacyStatus());
        po.setOrderStatus(domain.getOrderStatus());
        po.setPayStatus(domain.getPayStatus());
        po.setFulfillmentStatus(domain.getFulfillmentStatus());
        po.setRefundStatus(domain.getRefundStatus());
        po.setPaidAmount(domain.getPaidAmount());
        po.setRefundFrozenAmount(domain.getRefundFrozenAmount());
        po.setRefundedAmount(domain.getRefundedAmount());
        po.setExpireTime(domain.getExpireTime());
        po.setPaidTime(domain.getPaidTime());
        po.setReceiverName(domain.getReceiverName());
        po.setReceiverPhone(domain.getReceiverPhone());
        po.setReceiverAddress(domain.getReceiverAddress());
        po.setPaymentType(domain.getPaymentType());
        po.setPaymentAppId(domain.getPaymentAppId());
        po.setPaymentChannelCode(domain.getPaymentChannelCode());
        po.setVersion(domain.getVersion());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static OrderInfo toDomain(OrderInfoPO po) {
        if (po == null) {
            return null;
        }
        OrderInfo domain = new OrderInfo();
        domain.setId(po.getId());
        domain.setTitle(po.getTitle());
        domain.setOrderNo(po.getOrderNo());
        domain.setUserId(po.getUserId());
        domain.setProductId(po.getProductId());
        domain.setTotalFee(po.getTotalFee());
        domain.setCodeUrl(po.getCodeUrl());
        domain.setLegacyStatus(po.getLegacyStatus());
        domain.setOrderStatus(po.getOrderStatus());
        domain.setPayStatus(po.getPayStatus());
        domain.setFulfillmentStatus(po.getFulfillmentStatus());
        domain.setRefundStatus(po.getRefundStatus());
        domain.setPaidAmount(po.getPaidAmount());
        domain.setRefundFrozenAmount(po.getRefundFrozenAmount());
        domain.setRefundedAmount(po.getRefundedAmount());
        domain.setExpireTime(po.getExpireTime());
        domain.setPaidTime(po.getPaidTime());
        domain.setReceiverName(po.getReceiverName());
        domain.setReceiverPhone(po.getReceiverPhone());
        domain.setReceiverAddress(po.getReceiverAddress());
        domain.setPaymentType(po.getPaymentType());
        domain.setPaymentAppId(po.getPaymentAppId());
        domain.setPaymentChannelCode(po.getPaymentChannelCode());
        domain.setVersion(po.getVersion());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
