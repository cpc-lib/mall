package cc.ivera.payment.infrastructure.persistence.repository;

import cc.ivera.payment.domain.enums.PaymentOrderStatus;
import cc.ivera.payment.domain.model.PaymentOrder;
import cc.ivera.payment.domain.repository.PaymentOrderRepository;
import cc.ivera.payment.infrastructure.persistence.converter.PaymentOrderPOConverter;
import cc.ivera.payment.infrastructure.persistence.mapper.PaymentOrderMapper;
import cc.ivera.payment.infrastructure.persistence.po.PaymentOrderPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 支付单聚合仓储实现：MP Wrapper/XML CAS 全部收口于此，领域层只见端口。
 */
@Slf4j
@Repository
public class PaymentOrderRepositoryImpl implements PaymentOrderRepository {

    private final PaymentOrderMapper paymentOrderMapper;

    public PaymentOrderRepositoryImpl(PaymentOrderMapper paymentOrderMapper) {
        this.paymentOrderMapper = paymentOrderMapper;
    }

    @Override
    public void save(PaymentOrder paymentOrder) {
        PaymentOrderPO po = PaymentOrderPOConverter.toPO(paymentOrder);
        paymentOrderMapper.insert(po);
        paymentOrder.setId(po.getId());
        paymentOrder.setCreateTime(po.getCreateTime());
        paymentOrder.setUpdateTime(po.getUpdateTime());
    }

    @Override
    public PaymentOrder findByPaymentNoForUpdate(String paymentNo) {
        return PaymentOrderPOConverter.toDomain(paymentOrderMapper.selectByPaymentNoForUpdate(paymentNo));
    }

    @Override
    public PaymentOrder findActiveByOrderNoAndChannel(String orderNo, String channel) {
        return PaymentOrderPOConverter.toDomain(paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrderPO>()
            .eq(PaymentOrderPO::getOrderNo, orderNo)
            .eq(PaymentOrderPO::getChannel, channel)
            .in(PaymentOrderPO::getStatus,
                PaymentOrderStatus.CREATED.getType(), PaymentOrderStatus.PAYING.getType())
            .orderByDesc(PaymentOrderPO::getId)
            .last("limit 1")));
    }

    @Override
    public PaymentOrder findLatestSuccessByOrderNoAndChannel(String orderNo, String channel) {
        return PaymentOrderPOConverter.toDomain(paymentOrderMapper.selectOne(new LambdaQueryWrapper<PaymentOrderPO>()
            .eq(PaymentOrderPO::getOrderNo, orderNo)
            .eq(PaymentOrderPO::getChannel, channel)
            .eq(PaymentOrderPO::getStatus, PaymentOrderStatus.SUCCESS.getType())
            .orderByDesc(PaymentOrderPO::getId)
            .last("limit 1")));
    }

    @Override
    public List<PaymentOrder> listByOrderNoAsc(String orderNo) {
        return paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrderPO>()
                .eq(PaymentOrderPO::getOrderNo, orderNo)
                .orderByAsc(PaymentOrderPO::getId))
            .stream().map(PaymentOrderPOConverter::toDomain).collect(Collectors.toList());
    }

    @Override
    public int closeActiveByOrderNo(String orderNo) {
        return paymentOrderMapper.closeActiveByOrderNo(orderNo);
    }

    @Override
    public int closeActiveByOrderNoExceptPaymentNo(String orderNo, String paymentNo) {
        return paymentOrderMapper.closeActiveByOrderNoExceptPaymentNo(orderNo, paymentNo);
    }

    @Override
    public boolean markPaying(String paymentNo, String codeUrl) {
        return paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrderPO>()
            .eq(PaymentOrderPO::getPaymentNo, paymentNo)
            .eq(PaymentOrderPO::getStatus, PaymentOrderStatus.CREATED.getType())
            .set(PaymentOrderPO::getStatus, PaymentOrderStatus.PAYING.getType())
            .set(PaymentOrderPO::getCodeUrl, codeUrl)) > 0;
    }

    @Override
    public boolean markSuccess(String paymentNo, String channelOrderNo, Integer paidAmount) {
        return paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrderPO>()
            .eq(PaymentOrderPO::getPaymentNo, paymentNo)
            .in(PaymentOrderPO::getStatus,
                PaymentOrderStatus.CREATED.getType(), PaymentOrderStatus.PAYING.getType())
            .set(PaymentOrderPO::getStatus, PaymentOrderStatus.SUCCESS.getType())
            .set(PaymentOrderPO::getChannelOrderNo, channelOrderNo)
            .set(PaymentOrderPO::getPaidAmount, paidAmount)
            .set(PaymentOrderPO::getPaidTime, new Date())) > 0;
    }

    @Override
    public int freezeChannelRefund(String paymentNo, Integer amount) {
        return paymentOrderMapper.freezeChannelRefund(paymentNo, amount);
    }

    @Override
    public int settleChannelRefund(String paymentNo, Integer amount) {
        return paymentOrderMapper.settleChannelRefund(paymentNo, amount);
    }

    @Override
    public PaymentOrder findSuccessPaymentOrderForRefund(String orderNo) {
        List<PaymentOrderPO> list = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrderPO>()
            .eq(PaymentOrderPO::getOrderNo, orderNo)
            .eq(PaymentOrderPO::getStatus, PaymentOrderStatus.SUCCESS.getType())
            .orderByDesc(PaymentOrderPO::getPaidTime));
        if (!list.isEmpty()) {
            return PaymentOrderPOConverter.toDomain(list.get(0));
        }
        // 对账兜底：订单已 PAID 但支付单卡在 PAYING（旧代码支付成功未推进 PaymentOrder），
        // 此时订单级状态已是成交，支付单可安全推进为 SUCCESS。
        List<PaymentOrderPO> paying = paymentOrderMapper.selectList(new LambdaQueryWrapper<PaymentOrderPO>()
            .eq(PaymentOrderPO::getOrderNo, orderNo)
            .eq(PaymentOrderPO::getStatus, PaymentOrderStatus.PAYING.getType())
            .orderByDesc(PaymentOrderPO::getId));
        if (paying.isEmpty()) {
            return null;
        }
        PaymentOrderPO stuck = paying.get(0);
        log.warn("支付单卡在 PAYING 但订单已成交，对账推进 ===> paymentNo={}, orderNo={}", stuck.getPaymentNo(), orderNo);
        paymentOrderMapper.update(null, new LambdaUpdateWrapper<PaymentOrderPO>()
            .eq(PaymentOrderPO::getPaymentNo, stuck.getPaymentNo())
            .eq(PaymentOrderPO::getStatus, PaymentOrderStatus.PAYING.getType())
            .set(PaymentOrderPO::getStatus, PaymentOrderStatus.SUCCESS.getType())
            .set(PaymentOrderPO::getPaidAmount, stuck.getRequestAmount())
            .set(PaymentOrderPO::getPaidTime, new Date()));
        stuck.setStatus(PaymentOrderStatus.SUCCESS.getType());
        return PaymentOrderPOConverter.toDomain(stuck);
    }
}
