package cc.ivera.api.refund;

import cc.ivera.refund.domain.model.RefundOrder;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 退款账只读契约：供账单上下文核对平台退款账，不暴露退款仓储。
 */
public interface RefundLedgerQueryApi {

    List<RefundOrder> listByRefundNos(Collection<String> refundNos);

    List<RefundOrder> listSuccessBySuccessTimeRange(Date startInclusive, Date endExclusive);
}
