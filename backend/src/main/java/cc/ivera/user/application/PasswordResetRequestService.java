package cc.ivera.user.application;

import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.interfaces.dto.PasswordResetHandleRequest;
import cc.ivera.user.interfaces.dto.PasswordResetRejectRequest;
import cc.ivera.user.interfaces.dto.PasswordResetRequestSubmitRequest;
import cc.ivera.user.interfaces.vo.PasswordResetHandleVO;

import java.util.List;

/**
 * 找回密码申请：用户提交申请，管理员处理时由系统生成一次性随机密码。
 */
public interface PasswordResetRequestService {
    void submit(PasswordResetRequestSubmitRequest request);

    List<PasswordResetRequest> list();

    PasswordResetHandleVO handle(Long id, PasswordResetHandleRequest request);

    void reject(Long id, PasswordResetRejectRequest request);
}
