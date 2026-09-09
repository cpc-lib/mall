package cc.ivera.payment.infrastructure.persistence.repository;

import cc.ivera.payment.domain.model.PaymentInfo;
import cc.ivera.payment.domain.repository.PaymentInfoRepository;
import cc.ivera.payment.infrastructure.persistence.converter.PaymentInfoPOConverter;
import cc.ivera.payment.infrastructure.persistence.mapper.PaymentInfoMapper;
import cc.ivera.payment.infrastructure.persistence.po.PaymentInfoPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付流水仓储实现。唯一约束兜底重复通知，DuplicateKeyException 由应用层按幂等处理。
 */
@Repository
public class PaymentInfoRepositoryImpl implements PaymentInfoRepository {

    private final PaymentInfoMapper paymentInfoMapper;

    public PaymentInfoRepositoryImpl(PaymentInfoMapper paymentInfoMapper) {
        this.paymentInfoMapper = paymentInfoMapper;
    }

    @Override
    public void save(PaymentInfo paymentInfo) {
        PaymentInfoPO po = PaymentInfoPOConverter.toPO(paymentInfo);
        paymentInfoMapper.insert(po);
        paymentInfo.setId(po.getId());
        paymentInfo.setCreateTime(po.getCreateTime());
        paymentInfo.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public List<PaymentInfo> listByChannelAndOrderNos(String paymentType, Collection<String> orderNos) {
        if (orderNos == null || orderNos.isEmpty()) {
            return Collections.emptyList();
        }
        return paymentInfoMapper.selectList(new LambdaQueryWrapper<PaymentInfoPO>()
                .eq(PaymentInfoPO::getPaymentType, paymentType)
                .in(PaymentInfoPO::getOrderNo, orderNos))
            .stream().map(PaymentInfoPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public List<PaymentInfo> listByChannelAndStateAndCreateTimeRange(String paymentType, String tradeState,
                                                                     Date start, Date end) {
        return paymentInfoMapper.selectList(new LambdaQueryWrapper<PaymentInfoPO>()
                .eq(PaymentInfoPO::getPaymentType, paymentType)
                .eq(PaymentInfoPO::getTradeState, tradeState)
                .ge(PaymentInfoPO::getCreateTime, start)
                .lt(PaymentInfoPO::getCreateTime, end))
            .stream().map(PaymentInfoPOConverter::toDomain).collect(Collectors.toList());
    }
}
