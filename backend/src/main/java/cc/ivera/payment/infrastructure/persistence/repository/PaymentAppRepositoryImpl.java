package cc.ivera.payment.infrastructure.persistence.repository;

import cc.ivera.payment.domain.model.PaymentApp;
import cc.ivera.payment.domain.repository.PaymentAppRepository;
import cc.ivera.payment.infrastructure.persistence.converter.PaymentAppPOConverter;
import cc.ivera.payment.infrastructure.persistence.mapper.PaymentAppMapper;
import cc.ivera.payment.infrastructure.persistence.po.PaymentAppPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付应用仓储实现。
 */
@Repository
public class PaymentAppRepositoryImpl implements PaymentAppRepository {

    private static final String ENABLED = "ENABLED";

    private final PaymentAppMapper paymentAppMapper;

    public PaymentAppRepositoryImpl(PaymentAppMapper paymentAppMapper) {
        this.paymentAppMapper = paymentAppMapper;
    }

    @Override
    public void save(PaymentApp app) {
        PaymentAppPO po = PaymentAppPOConverter.toPO(app);
        paymentAppMapper.insert(po);
        app.setId(po.getId());
        app.setCreateTime(po.getCreateTime());
        app.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(PaymentApp app) {
        paymentAppMapper.updateById(PaymentAppPOConverter.toPO(app));
    }

    @Override
    public void deleteById(Long id) {
        paymentAppMapper.deleteById(id);
    }

    @Override
    public PaymentApp findById(Long id) {
        return PaymentAppPOConverter.toDomain(paymentAppMapper.selectById(id));
    }

    @Override
    public PaymentApp findByAppCode(String appCode) {
        return PaymentAppPOConverter.toDomain(paymentAppMapper.selectOne(new LambdaQueryWrapper<PaymentAppPO>()
            .eq(PaymentAppPO::getAppCode, appCode)));
    }

    @Override
    public List<PaymentApp> listByChannelId(Long channelId) {
        return list(new LambdaQueryWrapper<PaymentAppPO>()
            .eq(PaymentAppPO::getChannelId, channelId));
    }

    @Override
    public List<PaymentApp> listEnabled() {
        return list(new LambdaQueryWrapper<PaymentAppPO>()
            .eq(PaymentAppPO::getAppStatus, ENABLED));
    }

    @Override
    public List<PaymentApp> listAll() {
        return list(new LambdaQueryWrapper<PaymentAppPO>());
    }

    @Override
    public List<PaymentApp> listEnabledByChannelId(Long channelId) {
        return list(new LambdaQueryWrapper<PaymentAppPO>()
            .eq(PaymentAppPO::getChannelId, channelId)
            .eq(PaymentAppPO::getAppStatus, ENABLED));
    }

    private List<PaymentApp> list(LambdaQueryWrapper<PaymentAppPO> wrapper) {
        return paymentAppMapper.selectList(wrapper
                .orderByAsc(PaymentAppPO::getSortOrder)
                .orderByDesc(PaymentAppPO::getUpdateTime))
            .stream().map(PaymentAppPOConverter::toDomain).collect(Collectors.toList());
    }
}
