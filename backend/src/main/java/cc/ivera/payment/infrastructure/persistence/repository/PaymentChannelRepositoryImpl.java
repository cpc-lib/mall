package cc.ivera.payment.infrastructure.persistence.repository;

import cc.ivera.payment.domain.model.PaymentChannel;
import cc.ivera.payment.domain.repository.PaymentChannelRepository;
import cc.ivera.payment.infrastructure.persistence.converter.PaymentChannelPOConverter;
import cc.ivera.payment.infrastructure.persistence.mapper.PaymentChannelMapper;
import cc.ivera.payment.infrastructure.persistence.po.PaymentChannelPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付渠道仓储实现。
 */
@Repository
public class PaymentChannelRepositoryImpl implements PaymentChannelRepository {

    private static final String ENABLED = "ENABLED";

    private final PaymentChannelMapper paymentChannelMapper;

    public PaymentChannelRepositoryImpl(PaymentChannelMapper paymentChannelMapper) {
        this.paymentChannelMapper = paymentChannelMapper;
    }

    @Override
    public void save(PaymentChannel channel) {
        PaymentChannelPO po = PaymentChannelPOConverter.toPO(channel);
        paymentChannelMapper.insert(po);
        channel.setId(po.getId());
        channel.setCreateTime(po.getCreateTime());
        channel.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public void update(PaymentChannel channel) {
        paymentChannelMapper.updateById(PaymentChannelPOConverter.toPO(channel));
    }

    @Override
    public void deleteById(Long id) {
        paymentChannelMapper.deleteById(id);
    }

    @Override
    public PaymentChannel findById(Long id) {
        return PaymentChannelPOConverter.toDomain(paymentChannelMapper.selectById(id));
    }

    @Override
    public PaymentChannel findByChannelCode(String channelCode) {
        return PaymentChannelPOConverter.toDomain(paymentChannelMapper.selectOne(new LambdaQueryWrapper<PaymentChannelPO>()
            .eq(PaymentChannelPO::getChannelCode, channelCode)));
    }

    @Override
    public List<PaymentChannel> listEnabled() {
        return list(new LambdaQueryWrapper<PaymentChannelPO>()
            .eq(PaymentChannelPO::getChannelStatus, ENABLED));
    }

    @Override
    public List<PaymentChannel> listAll() {
        return list(new LambdaQueryWrapper<PaymentChannelPO>());
    }

    private List<PaymentChannel> list(LambdaQueryWrapper<PaymentChannelPO> wrapper) {
        return paymentChannelMapper.selectList(wrapper
                .orderByAsc(PaymentChannelPO::getSortOrder)
                .orderByDesc(PaymentChannelPO::getUpdateTime))
            .stream().map(PaymentChannelPOConverter::toDomain).collect(Collectors.toList());
    }
}
