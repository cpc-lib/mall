package cc.ivera.payment.application;

import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.interfaces.dto.PaymentChannelRequest;

import java.util.List;

public interface PaymentChannelService {

    /**
     * 根据渠道ID查询渠道信息
     */
    PaymentChannel getById(Long id);

    /**
     * 根据渠道编码查询渠道信息
     *
     * @param channelCode 渠道编码
     * @return 渠道信息
     */
    PaymentChannel getByChannelCode(String channelCode);

    /**
     * 获取所有启用的渠道
     *
     * @return 渠道列表
     */
    List<PaymentChannel> listEnabledChannels();

    /**
     * 获取全部渠道，配置管理页面使用。
     */
    List<PaymentChannel> listAllChannels();

    /**
     * 新增支付渠道配置。
     */
    PaymentChannel createChannel(PaymentChannelRequest request);

    /**
     * 修改支付渠道配置。
     */
    PaymentChannel updateChannel(Long id, PaymentChannelRequest request);

    /**
     * 修改渠道状态。
     */
    PaymentChannel updateChannelStatus(Long id, String status);

    /**
     * 删除支付渠道配置。
     */
    void deleteChannel(Long id);
}
