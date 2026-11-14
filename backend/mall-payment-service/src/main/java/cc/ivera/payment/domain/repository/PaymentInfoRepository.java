package cc.ivera.payment.domain.repository;

import cc.ivera.payment.domain.model.PaymentInfo;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 支付流水仓储端口。唯一约束兜底重复通知，DuplicateKeyException 由应用层按幂等处理。
 */
public interface PaymentInfoRepository {

    void save(PaymentInfo paymentInfo);

    /**
     * 渠道对账：按支付渠道 + 商户订单号集合查支付流水（账单正向核对）。
     */
    List<PaymentInfo> listByChannelAndOrderNos(String paymentType, Collection<String> orderNos);

    /**
     * 渠道对账反向扫描：指定渠道 + 交易状态 + 创建时间区间 [start, end) 的支付流水。
     */
    List<PaymentInfo> listByChannelAndStateAndCreateTimeRange(String paymentType, String tradeState,
                                                              Date start, Date end);
}
