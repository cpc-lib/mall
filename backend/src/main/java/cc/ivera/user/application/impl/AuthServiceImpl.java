package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.enums.CommonStatus;
import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.shared.security.AuthPrincipal;
import cc.ivera.shared.security.JwtTokenService;
import cc.ivera.shared.security.LoginGuardService;
import cc.ivera.shared.security.PasswordHasher;
import cc.ivera.user.application.AuthService;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.domain.repository.UserAccountRepository;
import cc.ivera.user.interfaces.dto.AdminResetPasswordRequest;
import cc.ivera.user.interfaces.dto.LoginRequest;
import cc.ivera.user.interfaces.dto.PasswordChangeRequest;
import cc.ivera.user.interfaces.dto.RegisterRequest;
import cc.ivera.user.interfaces.vo.LoginVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class AuthServiceImpl implements AuthService {
    private static final String ROLE_USER = "ROLE_USER";
    private static final String VERSION_PREFIX = "auth:token_version:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final long REFRESH_TTL_SECONDS = 7 * 24 * 3600L;

    private final UserAccountRepository userRepository;
    private final PasswordHasher passwordHasher;
    private final JwtTokenService jwtTokenService;
    private final StringRedisTemplate redisTemplate;
    private final LoginGuardService loginGuard;

    public AuthServiceImpl(UserAccountRepository userRepository, PasswordHasher passwordHasher, JwtTokenService jwtTokenService, StringRedisTemplate redisTemplate, LoginGuardService loginGuard) {
        this.userRepository = userRepository;
        this.passwordHasher = passwordHasher;
        this.jwtTokenService = jwtTokenService;
        this.redisTemplate = redisTemplate;
        this.loginGuard = loginGuard;
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
    public LoginVO login(LoginRequest request) {
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
        return issueTokenPair(user, version);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.trim().isEmpty()) throw new BizException("Refresh Token 不能为空");
        String key = REFRESH_PREFIX + sha256(refreshToken);
        DefaultRedisScript<String> consume = new DefaultRedisScript<>();
        consume.setResultType(String.class);
        consume.setScriptText("local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]); end; return v");
        String stored = redisTemplate.execute(consume, Collections.singletonList(key));
        if (stored == null) throw new BizException("Refresh Token 已失效或已被使用");
        String[] parts = stored.split(":", 2);
        Long userId = Long.valueOf(parts[0]);
        long tokenVersion = Long.parseLong(parts[1]);
        long currentVersion = ensureVersion(userId);
        if (currentVersion != tokenVersion) throw new BizException("Refresh Token 版本已失效");
        UserAccount user = userRepository.findById(userId);
        if (user == null || !CommonStatus.ENABLED.getType().equals(user.getUserStatus())) throw new BizException("用户不可用");
        return issueTokenPair(user, currentVersion);
    }

    @Override
    public void logout(AuthPrincipal principal, String refreshToken) {
        bumpVersion(principal.getUserId());
        if (refreshToken != null && !refreshToken.trim().isEmpty())
            redisTemplate.delete(REFRESH_PREFIX + sha256(refreshToken));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(AuthPrincipal principal, PasswordChangeRequest request) {
        UserAccount user = userRepository.findById(principal.getUserId());
        if (user == null || !passwordHasher.matches(request.getOldPassword(), user.getPasswordSalt(), user.getPasswordHash()))
            throw new BizException("原密码错误");
        resetPassword(user, request.getNewPassword());
        bumpVersion(user.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adminResetPassword(AdminResetPasswordRequest request) {
        UserAccount user = userRepository.findById(request.getUserId());
        if (user == null) throw new BizException("用户不存在");
        if ("ROLE_ADMIN".equals(user.getRole())) throw new BizException("该接口仅允许重置普通用户密码");
        resetPassword(user, request.getNewPassword());
        bumpVersion(user.getId());
        // 密码重置后解除该用户名的登录失败计数与锁定
        loginGuard.clearUsername(user.getUsername());
    }

    @Override
    public AuthPrincipal verifyAccessToken(String token) {
        AuthPrincipal p = jwtTokenService.parse(token);
        if (ensureVersion(p.getUserId()) != p.getTokenVersion()) throw new BizException("Access Token 已全局失效");
        return p;
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
        redisTemplate.opsForValue().increment(VERSION_PREFIX + userId);
    }

    private LoginVO issueTokenPair(UserAccount user, long version) {
        String access = jwtTokenService.issue(user.getId(), user.getUsername(), user.getRole(), version);
        String refresh = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        redisTemplate.opsForValue().set(REFRESH_PREFIX + sha256(refresh), user.getId() + ":" + version, REFRESH_TTL_SECONDS, TimeUnit.SECONDS);
        LoginVO result = new LoginVO();
        result.setAccessToken(access);
        result.setAccessExpiresIn(1800L);
        result.setRefreshToken(refresh);
        result.setRefreshExpiresIn(REFRESH_TTL_SECONDS);
        result.setUser(sanitize(user));
        return result;
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
