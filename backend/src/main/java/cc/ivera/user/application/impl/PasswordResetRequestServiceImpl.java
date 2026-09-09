package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthContext;
import cc.ivera.user.application.AuthService;
import cc.ivera.user.application.PasswordResetRequestService;
import cc.ivera.user.domain.model.PasswordResetRequest;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.domain.repository.PasswordResetRequestRepository;
import cc.ivera.user.domain.repository.UserAccountRepository;
import cc.ivera.user.interfaces.dto.AdminResetPasswordRequest;
import cc.ivera.user.interfaces.dto.PasswordResetHandleRequest;
import cc.ivera.user.interfaces.dto.PasswordResetRejectRequest;
import cc.ivera.user.interfaces.dto.PasswordResetRequestSubmitRequest;
import cc.ivera.user.interfaces.vo.PasswordResetHandleVO;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Comparator;
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
    public List<PasswordResetRequest> list() {
        List<PasswordResetRequest> list = requestRepository.listAll();
        list.sort(Comparator.comparing((PasswordResetRequest r) -> STATUS_PENDING.equals(r.getStatus()) ? 0 : 1)
            .thenComparing(Comparator.comparing(PasswordResetRequest::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))));
        return list;
    }

    @Override
    public PasswordResetHandleVO handle(Long id, PasswordResetHandleRequest request) {
        PasswordResetRequest row = requestRepository.findById(id);
        if (row == null) throw new BizException("申请不存在");
        if (!STATUS_PENDING.equals(row.getStatus())) throw new BizException("该申请已被处理，请刷新列表");
        UserAccount user = userRepository.findByUsername(row.getUsername());
        if (user == null) throw new BizException("用户不存在");
        String newPassword = generatePassword();
        AdminResetPasswordRequest resetRequest = new AdminResetPasswordRequest();
        resetRequest.setUserId(user.getId());
        resetRequest.setNewPassword(newPassword);
        authService.adminResetPassword(resetRequest);
        row.setStatus(STATUS_HANDLED);
        row.setHandledBy(AuthContext.get() == null ? null : AuthContext.get().getUserId());
        row.setAdminRemark(request == null ? null : request.getAdminRemark());
        requestRepository.update(row);
        PasswordResetHandleVO result = new PasswordResetHandleVO();
        result.setId(row.getId());
        result.setUsername(row.getUsername());
        result.setNewPassword(newPassword);
        return result;
    }

    @Override
    public void reject(Long id, PasswordResetRejectRequest request) {
        PasswordResetRequest row = requestRepository.findById(id);
        if (row == null) throw new BizException("申请不存在");
        if (!STATUS_PENDING.equals(row.getStatus())) throw new BizException("该申请已被处理，请刷新列表");
        row.setStatus(STATUS_REJECTED);
        row.setHandledBy(AuthContext.get() == null ? null : AuthContext.get().getUserId());
        row.setAdminRemark(request == null ? null : request.getAdminRemark());
        requestRepository.update(row);
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++)
            sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        return sb.toString();
    }
}
