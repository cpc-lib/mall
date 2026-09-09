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

    List<PaymentApp> listAll();

    List<PaymentApp> listEnabledByChannelId(Long channelId);
}
