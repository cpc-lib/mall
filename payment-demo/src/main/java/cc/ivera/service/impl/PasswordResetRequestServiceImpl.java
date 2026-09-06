package cc.ivera.service.impl;

import cc.ivera.dto.auth.AdminResetPasswordRequest;
import cc.ivera.dto.auth.PasswordResetHandleRequest;
import cc.ivera.dto.auth.PasswordResetRejectRequest;
import cc.ivera.dto.auth.PasswordResetRequestSubmitRequest;
import cc.ivera.entity.PasswordResetRequest;
import cc.ivera.entity.UserAccount;
import cc.ivera.exception.BizException;
import cc.ivera.mapper.PasswordResetRequestMapper;
import cc.ivera.mapper.UserAccountMapper;
import cc.ivera.security.AuthContext;
import cc.ivera.service.AuthService;
import cc.ivera.service.PasswordResetRequestService;
import cc.ivera.vo.PasswordResetHandleVO;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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

    private final PasswordResetRequestMapper requestMapper;
    private final UserAccountMapper userMapper;
    private final AuthService authService;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetRequestServiceImpl(PasswordResetRequestMapper requestMapper, UserAccountMapper userMapper, AuthService authService) {
        this.requestMapper = requestMapper;
        this.userMapper = userMapper;
        this.authService = authService;
    }

    @Override
    public void submit(PasswordResetRequestSubmitRequest request) {
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        UserAccount user = findByUsername(username);
        if (user == null || ROLE_ADMIN.equals(user.getRole())) throw new BizException("用户不存在或不可申请");
        Integer pending = requestMapper.selectCount(new QueryWrapper<PasswordResetRequest>().eq("username", username).eq("status", STATUS_PENDING));
        if (pending != null && pending > 0) throw new BizException("该账号已有待处理的找回密码申请，请等待管理员处理");
        PasswordResetRequest row = new PasswordResetRequest();
        row.setUsername(username);
        row.setRemark(request.getRemark());
        row.setStatus(STATUS_PENDING);
        requestMapper.insert(row);
    }

    @Override
    public List<PasswordResetRequest> list() {
        List<PasswordResetRequest> list = requestMapper.selectList(null);
        list.sort(Comparator.comparing((PasswordResetRequest r) -> STATUS_PENDING.equals(r.getStatus()) ? 0 : 1)
                .thenComparing(Comparator.comparing(PasswordResetRequest::getCreateTime, Comparator.nullsLast(Comparator.reverseOrder()))));
        return list;
    }

    @Override
    public PasswordResetHandleVO handle(Long id, PasswordResetHandleRequest request) {
        PasswordResetRequest row = requestMapper.selectById(id);
        if (row == null) throw new BizException("申请不存在");
        if (!STATUS_PENDING.equals(row.getStatus())) throw new BizException("该申请已被处理，请刷新列表");
        UserAccount user = findByUsername(row.getUsername());
        if (user == null) throw new BizException("用户不存在");
        String newPassword = generatePassword();
        AdminResetPasswordRequest resetRequest = new AdminResetPasswordRequest();
        resetRequest.setUserId(user.getId());
        resetRequest.setNewPassword(newPassword);
        authService.adminResetPassword(resetRequest);
        row.setStatus(STATUS_HANDLED);
        row.setHandledBy(AuthContext.get() == null ? null : AuthContext.get().getUserId());
        row.setAdminRemark(request == null ? null : request.getAdminRemark());
        requestMapper.updateById(row);
        PasswordResetHandleVO result = new PasswordResetHandleVO();
        result.setId(row.getId());
        result.setUsername(row.getUsername());
        result.setNewPassword(newPassword);
        return result;
    }

    @Override
    public void reject(Long id, PasswordResetRejectRequest request) {
        PasswordResetRequest row = requestMapper.selectById(id);
        if (row == null) throw new BizException("申请不存在");
        if (!STATUS_PENDING.equals(row.getStatus())) throw new BizException("该申请已被处理，请刷新列表");
        row.setStatus(STATUS_REJECTED);
        row.setHandledBy(AuthContext.get() == null ? null : AuthContext.get().getUserId());
        row.setAdminRemark(request == null ? null : request.getAdminRemark());
        requestMapper.updateById(row);
    }

    private UserAccount findByUsername(String username) {
        QueryWrapper<UserAccount> q = new QueryWrapper<>(); q.eq("username", username);
        return userMapper.selectOne(q);
    }

    private String generatePassword() {
        StringBuilder sb = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) sb.append(PASSWORD_ALPHABET.charAt(random.nextInt(PASSWORD_ALPHABET.length())));
        return sb.toString();
    }
}
