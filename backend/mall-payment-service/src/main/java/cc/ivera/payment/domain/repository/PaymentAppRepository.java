package cc.ivera.payment.domain.repository;

import cc.ivera.payment.domain.model.PaymentApp;

import java.util.List;

/**
 * 支付应用仓储端口。
 */
public interface PaymentAppRepository {

    void save(PaymentApp app);

    void update(PaymentApp app);

    void deleteById(Long id);

    PaymentApp findById(Long id);

    PaymentApp findByAppCode(String appCode);

    List<PaymentApp> listByChannelId(Long channelId);

    List<PaymentApp> listEnabled();

    /**
     * 全部应用总数（配置管理页分页使用）。
     */
    long countAll();

    /**
     * 全部应用分页，排序与列表一致：sort_order 升序、update_time 倒序。
     */
    List<PaymentApp> listAllPage(int limit, long offset);

    List<PaymentApp> listEnabledByChannelId(Long channelId);
}
