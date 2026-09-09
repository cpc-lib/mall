package cc.ivera.payment.infrastructure.persistence.mapper;

import cc.ivera.payment.infrastructure.persistence.po.PaymentOrderPO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentOrderMapper extends BaseMapper<PaymentOrderPO> {

    /**
     * 按支付单编号普通查询（不加锁）：事务提交后的渠道调用/状态读取使用。
     */
    PaymentOrderPO selectByPaymentNo(@Param("paymentNo") String paymentNo);

    /**
     * 渠道资金冻结：累计退款不超实付（refunded + frozen + amount <= paid）。
     */
    int freezeChannelRefund(@Param("paymentNo") String paymentNo, @Param("amount") Integer amount);

    /**
     * 渠道退款成功结转：冻结转已退。
     */
    int settleChannelRefund(@Param("paymentNo") String paymentNo, @Param("amount") Integer amount);

    /**
     * 关闭订单下全部活跃支付单（CREATED/PAYING → CLOSED）。
     */
    int closeActiveByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 订单成交收口：关闭除成交支付单外的其它渠道活跃支付单。
     */
    int closeActiveByOrderNoExceptPaymentNo(@Param("orderNo") String orderNo, @Param("paymentNo") String paymentNo);
}
