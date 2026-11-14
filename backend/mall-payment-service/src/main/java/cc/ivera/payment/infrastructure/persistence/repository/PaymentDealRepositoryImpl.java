package cc.ivera.payment.infrastructure.persistence.repository;

import cc.ivera.payment.domain.model.PaymentDeal;
import cc.ivera.payment.domain.repository.PaymentDealRepository;
import cc.ivera.payment.infrastructure.persistence.converter.PaymentDealPOConverter;
import cc.ivera.payment.infrastructure.persistence.mapper.PaymentDealMapper;
import cc.ivera.payment.infrastructure.persistence.po.PaymentDealPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;

/**
 * 有效支付成交事实仓储实现：唯一键冲突转 false，其它异常原样抛出。
 */
@Repository
public class PaymentDealRepositoryImpl implements PaymentDealRepository {

    private final PaymentDealMapper paymentDealMapper;

    public PaymentDealRepositoryImpl(PaymentDealMapper paymentDealMapper) {
        this.paymentDealMapper = paymentDealMapper;
    }

    @Override
    public boolean insert(PaymentDeal deal) {
        try {
            paymentDealMapper.insert(PaymentDealPOConverter.toPO(deal));
            return true;
        } catch (DuplicateKeyException e) {
            return false;
        }
    }

    @Override
    public PaymentDeal findByOrderNo(String orderNo) {
        return PaymentDealPOConverter.toDomain(selectOne(
            new LambdaQueryWrapper<PaymentDealPO>().eq(PaymentDealPO::getOrderNo, orderNo)));
    }

    @Override
    public PaymentDeal findByPaymentNo(String paymentNo) {
        return PaymentDealPOConverter.toDomain(selectOne(
            new LambdaQueryWrapper<PaymentDealPO>().eq(PaymentDealPO::getPaymentNo, paymentNo)));
    }

    @Override
    public boolean existsByOrderNo(String orderNo) {
        return paymentDealMapper.selectCount(
            new LambdaQueryWrapper<PaymentDealPO>().eq(PaymentDealPO::getOrderNo, orderNo)) > 0;
    }

    private PaymentDealPO selectOne(LambdaQueryWrapper<PaymentDealPO> wrapper) {
        return paymentDealMapper.selectOne(wrapper.last("limit 1"));
    }
}
