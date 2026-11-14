package cc.ivera.order.infrastructure.remote;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.domain.exception.ErrorCode;
import cc.ivera.shared.web.R;

/**
 * 远程调用 R 信封解包：null 响应、业务错误码统一转为 BizException。
 */
final class RemoteResponseUnwrapper {

    private RemoteResponseUnwrapper() {
    }

    static <T> T unwrap(R<T> response, String serviceLabel) {
        if (response == null) throw new BizException(serviceLabel + "响应为空");
        if (response.getCode() == null || response.getCode() != 0)
            throw remoteException(response.getCode(), response.getMessage(), serviceLabel);
        return response.getData();
    }

    private static BizException remoteException(Integer code, String message, String serviceLabel) {
        for (ErrorCode value : ErrorCode.values()) {
            if (code != null && code == value.getCode())
                return new BizException(value, message == null ? value.getDefaultMessage() : message);
        }
        return new BizException(message == null ? serviceLabel + "调用失败" : message);
    }
}
