package cc.ivera.refund.infrastructure.remote;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;

/**
 * 退款服务远程调用公共实现：R 信封解包与错误码映射。
 */
abstract class AbstractRefundRemoteAdapter {

    protected <T> T unwrap(R<T> response) {
        if (response == null) throw new BizException("远程服务响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage());
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? "远程服务调用失败" : message);
    }
}
