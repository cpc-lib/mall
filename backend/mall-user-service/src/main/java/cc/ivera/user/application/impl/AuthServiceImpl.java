package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.security.JwtTokenService;
import cc.ivera.shared.security.LoginGuardService;
import cc.ivera.shared.security.PasswordHasher;
import cc.ivera.user.application.AuthService;
import cc.ivera.user.domain.model.AuthSession;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.domain.repository.AuthSessionRepository;
import cc.ivera.user.domain.repository.UserAccountRepository;
import cc.ivera.user.application.dto.AdminResetPasswordRequest;
import cc.ivera.user.application.dto.LoginRequest;
import cc.ivera.user.application.dto.PasswordChangeRequest;
import cc.ivera.user.application.dto.RegisterRequest;
import cc.ivera.user.application.vo.LoginVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.UUID;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String ROLE_USER = "ROLE_USER";
    private static final String VERSION_PREFIX = "auth:token_version:";
    private static final long REFRESH_TTL_SECONDS = 7 * 24 * 3600L;

    private final UserAccountRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService jwtTokenService;
    private final StringRedisTemplate redisTemplate;
    private final LoginGuardService loginGuard;
    private final AuthSessionRepository authSessionRepository;

    public AuthServiceImpl(UserAccountRepository userRepository, PasswordHasher passwordHasher, JwtTokenService jwtTokenService, StringRedisTemplate redisTemplate, LoginGuardService loginGuard, AuthSessionRepository authSessionRepository) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
        this.loginGuard = loginGuard;
        this.authSessionRepository = authSessionRepository;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAccount register(RegisterRequest request) {
        UserAccount user = new UserAccount();
        user.setUsername(request.getUsername().trim());
        String salt = passwordHasher.newSalt();
        user.setPasswordSalt(salt);
        user.setPasswordHash(passwordHasher.hash(request.getPassword(), salt));
        user.setRole(ROLE_USER);
        user.setUserStatus(CommonStatus.ENABLED.getType());
        try {
            userRepository.save(user);
        } catch (DuplicateKeyException e) {
            throw new BizException("用户名已存在", e);
        }
        ensureVersion(user.getId());
        return sanitize(user);
    }

    @Override
    public LoginVO login(LoginRequest request, String ip, String userAgent) {
        String username = request.getUsername() == null ? null : request.getUsername().trim();
        loginGuard.checkLocked(username);
        UserAccount user = userRepository.findByUsername(username);
        boolean userMissing = user == null;
        boolean passwordWrong = !userMissing && !passwordHasher.matches(request.getPassword(), user.getPasswordSalt(), user.getPasswordHash());
        if (userMissing || passwordWrong) {
            loginGuard.recordFailure(username);
            throw new BizException("用户名或密码错误");
        }
        if (!CommonStatus.ENABLED.getType().equals(user.getUserStatus())) {
            // 现状：禁用用户统一返回“用户名或密码错误”，且不计入登录失败计数
            throw new BizException("用户名或密码错误");
        }
        loginGuard.clear(username);
        long version = ensureVersion(user.getId());
        return issueSession(user, version, ip, userAgent);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) throw new BizException("Refresh Token 不能为空");
        String tokenHash = sha256(refreshToken);
        String stored = authSessionRepository.consumeRefresh(tokenHash);
        if (stored == null) {
            // 旧 Token 再次出现：判定为重放，吊销整个会话
            String reusedSid = authSessionRepository.findUsedRefreshSid(tokenHash);
            if (reusedSid != null) authSessionRepository.delete(reusedSid);
            throw new BizException("Refresh Token 已失效或已被使用");
        }
        String[] parts = stored.split(":");
        if (parts.length != 3) throw new BizException("Refresh Token 已失效或已被使用");
        Long userId = Long.valueOf(parts[0]);
        long tokenVersion = Long.parseLong(parts[1]);
        String sid = parts[2];

        long currentVersion = ensureVersion(userId);
        if (currentVersion != tokenVersion) {
            authSessionRepository.delete(sid);
            throw new BizException("Refresh Token 版本已失效");
        }
        AuthSession session = authSessionRepository.find(sid);
        if (session == null || !userId.equals(session.getUserId())) throw new BizException("会话已失效");
        UserAccount user = userRepository.findById(userId);
        if (user == null || !CommonStatus.ENABLED.getType().equals(user.getUserStatus()))
            throw new BizException("用户不可用");

        String newRefresh = newRefreshToken();
        String newHash = sha256(newRefresh);
        long now = System.currentTimeMillis();
        authSessionRepository.rotate(sid, newHash, now, REFRESH_TTL_SECONDS);
        authSessionRepository.saveRefresh(newHash, userId, currentVersion, sid, REFRESH_TTL_SECONDS);
        authSessionRepository.markRefreshUsed(tokenHash, sid, REFRESH_TTL_SECONDS);
        return buildLoginVO(user, currentVersion, sid, newRefresh);
    }

    @Override
    public void logout(AuthPrincipal principal, String refreshToken) {
        if (principal != null && principal.getSid() != null) {
            // 双 Token 模型：只下线当前设备，不再踢掉该用户所有会话
            authSessionRepository.delete(principal.getSid());
            return;
        }
        // 兼容旧 Token：无 sid 时退回全局下线
        bumpVersion(principal == null ? null : principal.getUserId());
        if (refreshToken != null && !refreshToken.trim().isEmpty())
            authSessionRepository.deleteRefresh(sha256(refreshToken));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(AuthPrincipal principal, PasswordChangeRequest request) {
        UserAccount user = userRepository.findById(principal.getUserId());
        if (user == null || !passwordHasher.matches(request.getOldPassword(), user.getPasswordSalt(), user.getPasswordHash()))
            throw new BizException("原密码错误");
        resetPassword(user, request.getNewPassword());
        bumpVersion(user.getId());
        authSessionRepository.deleteAll(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminResetPassword(AdminResetPasswordRequest request) {
        UserAccount user = userRepository.findById(request.getUserId());
        if (user == null) throw new BizException("用户不存在");
        if ("ROLE_ADMIN".equals(user.getRole())) throw new BizException("该接口仅允许重置普通用户密码");
        resetPassword(user, request.getNewPassword());
        bumpVersion(user.getId());
        authSessionRepository.deleteAll(user.getId());
        // 密码重置后解除该用户名的登录失败计数与锁定
        loginGuard.clearUsername(user.getUsername());
    }

    @Override
    public AuthPrincipal verifyAccessToken(String token) {
        AuthPrincipal p = jwtTokenService.parse(token);
        if (ensureVersion(p.getUserId()) != p.getTokenVersion()) throw new BizException("Access Token 已全局失效");
        if (p.getSid() != null) {
            AuthSession session = authSessionRepository.find(p.getSid());
            if (session == null || !p.getUserId().equals(session.getUserId())) throw new BizException("会话已失效，请重新登录");
        }
        return p;
    }

    private LoginVO issueSession(UserAccount user, long version, String ip, String userAgent) {
        String sid = UUID.randomUUID().toString().replace("-", "");
        String refresh = newRefreshToken();
        String refreshHash = sha256(refresh);
        long now = System.currentTimeMillis();

        AuthSession session = new AuthSession();
        session.setSid(sid);
        session.setUserId(user.getId());
        session.setRefreshHash(refreshHash);
        session.setIssuedAt(now);
        session.setLastSeenAt(now);
        session.setIp(ip);
        session.setUserAgent(userAgent);
        authSessionRepository.create(session, REFRESH_TTL_SECONDS);
        authSessionRepository.saveRefresh(refreshHash, user.getId(), version, sid, REFRESH_TTL_SECONDS);
        return buildLoginVO(user, version, sid, refresh);
    }

    private LoginVO buildLoginVO(UserAccount user, long version, String sid, String refresh) {
        String access = jwtTokenService.issue(user.getId(), user.getUsername(), user.getRole(), version, sid);
        LoginVO result = new LoginVO();
        result.setAccessToken(access);
        result.setAccessExpiresIn(jwtTokenService.getAccessTtlSeconds());
        result.setRefreshToken(refresh);
        result.setRefreshExpiresIn(REFRESH_TTL_SECONDS);
        result.setUser(sanitize(user));
        return result;
    }

    private void resetPassword(UserAccount user, String password) {
        String salt = passwordHasher.newSalt();
        user.setPasswordSalt(salt);
        user.setPasswordHash(passwordHasher.hash(password, salt));
        userRepository.update(user);
    }

    private long ensureVersion(Long userId) {
        String key = VERSION_PREFIX + userId;
        redisTemplate.opsForValue().setIfAbsent(key, "0");
        String value = redisTemplate.opsForValue().get(key);
        return value == null ? 0L : Long.parseLong(value);
    }

    private void bumpVersion(Long userId) {
        if (userId == null) return;
        redisTemplate.opsForValue().increment(VERSION_PREFIX + userId);
    }

    private String newRefreshToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private UserAccount sanitize(UserAccount source) {
        UserAccount u = new UserAccount();
        u.setId(source.getId());
        u.setUsername(source.getUsername());
        u.setRole(source.getRole());
        u.setUserStatus(source.getUserStatus());
        u.setCreateTime(source.getCreateTime());
        u.setUpdateTime(source.getUpdateTime());
        return u;
    }

    private String sha256(String input) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new BizException("Token 摘要失败", e);
        }
    }
}
