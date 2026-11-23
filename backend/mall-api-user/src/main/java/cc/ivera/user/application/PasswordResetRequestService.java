package cc.ivera.user.application;

import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.application.dto.PasswordResetHandleRequest;
import cc.ivera.user.application.dto.PasswordResetRejectRequest;
import cc.ivera.user.application.dto.PasswordResetRequestSubmitRequest;
import cc.ivera.user.application.vo.PasswordResetHandleVO;
import cc.ivera.shared.web.PageVO;

/**
 * 找回密码申请：用户提交申请，管理员处理时由系统生成一次性随机密码。
 */
public interface PasswordResetRequestService {
    void submit(PasswordResetRequestSubmitRequest request);

    /**
     * 管理员申请列表（分页，待处理优先，同状态按申请时间倒序）。
     */
    PageVO<PasswordResetRequest> list(int page, int size);

    PasswordResetHandleVO handle(Long id, PasswordResetHandleRequest request, Long operatorUserId);

    void reject(Long id, PasswordResetRejectRequest request, Long operatorUserId);
}
