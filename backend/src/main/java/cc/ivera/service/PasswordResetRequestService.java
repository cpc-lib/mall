package cc.ivera.service;

import cc.ivera.dto.auth.PasswordResetHandleRequest;
import cc.ivera.dto.auth.PasswordResetRejectRequest;
import cc.ivera.dto.auth.PasswordResetRequestSubmitRequest;
import cc.ivera.entity.PasswordResetRequest;
import cc.ivera.vo.PasswordResetHandleVO;

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
