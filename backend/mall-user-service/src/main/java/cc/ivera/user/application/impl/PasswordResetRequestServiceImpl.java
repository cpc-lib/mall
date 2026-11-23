package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.user.application.AuthService;
import cc.ivera.user.application.PasswordResetRequestService;
import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.domain.repository.PasswordResetRequestRepository;
import cc.ivera.user.domain.repository.UserAccountRepository;
import cc.ivera.user.application.dto.AdminResetPasswordRequest;
import cc.ivera.user.application.dto.PasswordResetHandleRequest;
import cc.ivera.user.application.dto.PasswordResetRejectRequest;
import cc.ivera.user.application.dto.PasswordResetRequestSubmitRequest;
import cc.ivera.user.application.vo.PasswordResetHandleVO;
import cc.ivera.shared.web.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class PasswordResetRequestServiceImpl implements PasswordResetRequestService {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_HANDLED = "HANDLED";
    private static final String STATUS_REJECTED = "REJECTED";
    private static final String ROLE_ADMIN = "ROLE_ADMIN";
    private static final String PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final int PASSWORD_LENGTH = 12;

    private final PasswordResetRequestRepository requestRepository;
    private final UserAccountRepository userRepository;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetRequestServiceImpl(PasswordResetRequestRepository requestRepository, UserAccountRepository userRepository, AuthService authService) {
        this.requestRepository = requestRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @Override
    public void submit(PasswordResetRequestSubmitRequest request) {
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        UserAccount user = userRepository.findByUsername(username);
        if (user == null || ROLE_ADMIN.equals(user.getRole())) throw new BizException("用户不存在或不可申请");
        if (requestRepository.countPendingByUsername(username) > 0)
            throw new BizException("该账号已有待处理的找回密码申请，请等待管理员处理");
        PasswordResetRequest row = new PasswordResetRequest();
        row.setUsername(username);
        row.setRemark(request.getRemark());
        row.setStatus(STATUS_PENDING);
        requestRepository.save(row);
    }

    @Override
    public PageVO<PasswordResetRequest> list(int page, int size) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageVO<PasswordResetRequest> result = new PageVO<>();
        result.setTotal(requestRepository.countAll());
        result.setPage(safePage);
        result.setSize(safeSize);
        result.setRecords(requestRepository.listPagePendingFirst(safeSize, (long) (safePage - 1) * safeSize));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PasswordResetHandleVO handle(Long id, PasswordResetHandleRequest request, Long operatorUserId) {
        // 1. 快速检查（非权威，仅提前失败）
        PasswordResetRequest row = requestRepository.findById(id);
        if (row == null) throw new BizException("申请不存在");
        if (!STATUS_PENDING.equals(row.getStatus())) throw new BizException("该申请已被处理，请刷新列表");
        UserAccount user = userRepository.findByUsername(row.getUsername());
        if (user == null) throw new BizException("用户不存在");

        // 2. 条件 UPDATE CAS：仅当状态仍为 PENDING 时置为 HANDLED，确保并发 handle + reject 互斥
        int rows = requestRepository.markResolvedIfStatusIn(id, List.of(STATUS_PENDING), STATUS_HANDLED,
            operatorUserId, request == null ? null : request.getAdminRemark());
        if (rows == 0) throw new BizException("该申请已被其他管理员处理，请刷新列表");

        // 3. CAS 成功后再执行副作用（密码重置），失败则外层事务回滚
        String newPassword = generatePassword();
        AdminResetPasswordRequest resetRequest = new AdminResetPasswordRequest();
        resetRequest.setUserId(user.getId());
        resetRequest.setNewPassword(newPassword);
        authService.adminResetPassword(resetRequest);

        PasswordResetHandleVO result = new PasswordResetHandleVO();
        result.setId(id);
        result.setUsername(row.getUsername());
        result.setNewPassword(newPassword);
        return result;
    }

    @Override
    public void reject(Long id, PasswordResetRejectRequest request, Long operatorUserId) {
        // 快速检查（非权威）
        PasswordResetRequest row = requestRepository.findById(id);
        if (row == null) throw new BizException("申请不存在");
        if (!STATUS_PENDING.equals(row.getStatus())) throw new BizException("该申请已被处理，请刷新列表");

        // 条件 UPDATE CAS：仅当状态仍为 PENDING 时置为 REJECTED
        int rows = requestRepository.markResolvedIfStatusIn(id, List.of(STATUS_PENDING), STATUS_REJECTED,
            operatorUserId, request == null ? null : request.getAdminRemark());
        if (rows == 0) throw new BizException("该申请已被其他管理员处理，请刷新列表");
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++)
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        return sb.toString();
    }
}
