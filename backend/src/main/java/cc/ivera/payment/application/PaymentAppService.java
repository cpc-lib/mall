package cc.ivera.payment.application;

import cc.ivera.payment.interfaces.dto.PaymentAppRequest;
import cc.ivera.payment.domain.model.PaymentApp;

import java.util.List;

public interface PaymentAppService {

    /**
     * 根据应用ID查询应用信息
     */
    PaymentApp getById(Long id);

    /**
     * 根据应用编码查询应用信息
     *
     * @param appCode 应用编码
     * @return 应用信息
     */
    PaymentApp getByAppCode(String appCode);

    /**
     * 根据渠道ID查询应用列表
     *
     * @param channelId 渠道ID
     * @return 应用列表
     */
    List<PaymentApp> listByChannelId(Long channelId);

    /**
     * 获取所有启用的应用
     *
     * @return 应用列表
     */
    List<PaymentApp> listEnabledApps();

    /**
     * 获取所有应用，配置管理页面使用。
     */
    List<PaymentApp> listAllApps();

    /**
     * 根据渠道ID获取启用的应用列表
     *
     * @param channelId 渠道ID
     * @return 应用列表
     */
    List<PaymentApp> listEnabledAppsByChannelId(Long channelId);

    /**
     * 新增支付应用配置。
     */
    PaymentApp createApp(PaymentAppRequest request);

    /**
     * 修改支付应用配置。
     */
    PaymentApp updateApp(Long id, PaymentAppRequest request);

    /**
     * 修改支付应用状态。
     */
    PaymentApp updateAppStatus(Long id, String status);

    /**
     * 删除支付应用配置。
     */
    void deleteApp(Long id);
}
