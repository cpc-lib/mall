package cc.ivera.payment.infrastructure.persistence.converter;

import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.infrastructure.persistence.po.PaymentChannelPO;

/**
 * PaymentChannel ↔ PaymentChannelPO 转换（字段同名手写映射）。
 */
public final class PaymentChannelPOConverter {

    private PaymentChannelPOConverter() {
    }

    public static PaymentChannelPO toPO(PaymentChannel domain) {
        if (domain == null) {
            return null;
        }
        PaymentChannelPO po = new PaymentChannelPO();
        po.setId(domain.getId());
        po.setChannelName(domain.getChannelName());
        po.setChannelCode(domain.getChannelCode());
        po.setChannelStatus(domain.getChannelStatus());
        po.setChannelDesc(domain.getChannelDesc());
        po.setConfigParams(domain.getConfigParams());
        po.setAppid(domain.getAppid());
        po.setMchId(domain.getMchId());
        po.setMchSerialNo(domain.getMchSerialNo());
        po.setPrivateKey(domain.getPrivateKey());
        po.setApiV3Key(domain.getApiV3Key());
        po.setPartnerKey(domain.getPartnerKey());
        po.setAlipayAppId(domain.getAlipayAppId());
        po.setSellerId(domain.getSellerId());
        po.setMerchantPrivateKey(domain.getMerchantPrivateKey());
        po.setAlipayPublicKey(domain.getAlipayPublicKey());
        po.setSortOrder(domain.getSortOrder());
        po.setCreateTime(domain.getCreateTime());
        po.setUpdateTime(domain.getUpdateTime());
        return po;
    }

    public static PaymentChannel toDomain(PaymentChannelPO po) {
        if (po == null) {
            return null;
        }
        PaymentChannel domain = new PaymentChannel();
        domain.setId(po.getId());
        domain.setChannelName(po.getChannelName());
        domain.setChannelCode(po.getChannelCode());
        domain.setChannelStatus(po.getChannelStatus());
        domain.setChannelDesc(po.getChannelDesc());
        domain.setConfigParams(po.getConfigParams());
        domain.setAppid(po.getAppid());
        domain.setMchId(po.getMchId());
        domain.setMchSerialNo(po.getMchSerialNo());
        domain.setPrivateKey(po.getPrivateKey());
        domain.setApiV3Key(po.getApiV3Key());
        domain.setPartnerKey(po.getPartnerKey());
        domain.setAlipayAppId(po.getAlipayAppId());
        domain.setSellerId(po.getSellerId());
        domain.setMerchantPrivateKey(po.getMerchantPrivateKey());
        domain.setAlipayPublicKey(po.getAlipayPublicKey());
        domain.setSortOrder(po.getSortOrder());
        domain.setCreateTime(po.getCreateTime());
        domain.setUpdateTime(po.getUpdateTime());
        return domain;
    }
}
