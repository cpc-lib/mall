package cc.ivera.payment.domain.repository;

import cc.ivera.payment.domain.model.PaymentChannel;

import java.util.List;

/**
 * 支付渠道仓储端口。
 */
public interface PaymentChannelRepository {

    void save(PaymentChannel channel);

    void update(PaymentChannel channel);

    void deleteById(Long id);

    PaymentChannel findById(Long id);

    PaymentChannel findByChannelCode(String channelCode);

    List<PaymentChannel> listEnabled();

    List<PaymentChannel> listAll();
}
