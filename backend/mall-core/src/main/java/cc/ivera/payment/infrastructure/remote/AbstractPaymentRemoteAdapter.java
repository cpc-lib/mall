package cc.ivera.payment.infrastructure.remote;

import cc.ivera.payment.infrastructure.remote.client.PaymentServiceClient;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;

/**
 * 支付服务远程调用公共实现：Feign 客户端 + R 解包。
 */
abstract class AbstractPaymentRemoteAdapter {

    protected final PaymentServiceClient client;

    protected AbstractPaymentRemoteAdapter(PaymentServiceClient client) {
        this.client = client;
    }

    protected <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("支付服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "支付服务调用失败" : message);
    }
}
