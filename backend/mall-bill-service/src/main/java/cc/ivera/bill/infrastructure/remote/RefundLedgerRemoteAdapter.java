package cc.ivera.bill.infrastructure.remote;

import cc.ivera.api.refund.RefundLedgerQueryApi;
import cc.ivera.bill.infrastructure.remote.client.RefundLedgerFeignClient;
import cc.ivera.refund.domain.model.RefundOrder;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Date;
import java.util.List;

/**
 * 退款账远程适配器：账单上下文通过 refund-service 内部契约读取平台退款账。
 */
@Component
public class RefundLedgerRemoteAdapter implements RefundLedgerQueryApi {

    private final RefundLedgerFeignClient client;

    public RefundLedgerRemoteAdapter(RefundLedgerFeignClient client) {
        this.client = client;
    }

    @Override
    public List<RefundOrder> listByRefundNos(Collection<String> refundNos) {
        return unwrap(client.listByRefundNos(String.join(",", refundNos)));
    }

    @Override
    public List<RefundOrder> listSuccessBySuccessTimeRange(Date startInclusive, Date endExclusive) {
        return unwrap(client.listSuccessBySuccessTimeRange(startInclusive.getTime(), endExclusive.getTime()));
    }

    private <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("退款服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "退款服务调用失败" : message);
    }
}
