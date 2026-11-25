package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.user.domain.gateway.LoginGuardPort;
import cc.ivera.user.domain.gateway.PasswordHashPort;
import cc.ivera.user.domain.gateway.TokenPort;
import cc.ivera.user.domain.gateway.TokenVersionPort;
import cc.ivera.user.domain.model.AuthSession;
import cc.ivera.user.domain.model.UserAccount;
import cc.ivera.user.domain.repository.AuthSessionRepository;
import cc.ivera.user.domain.repository.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthServiceImpl refresh token Mockito 单测：覆盖 Replay Detection + 版本失效 + 正常轮换核心路径。
 * sha256/newRefreshToken 是私有纯计算，直接执行不 mock。
 */
class AuthServiceImplRefreshTest {

    private UserAccountRepository userRepo = mock(UserAccountRepository.class);
    private PasswordHashPort passwordHasher = mock(PasswordHashPort.class);
    private TokenPort jwtToken = mock(TokenPort.class);
    private TokenVersionPort versionStore = mock(TokenVersionPort.class);
    private LoginGuardPort loginGuard = mock(LoginGuardPort.class);
    private AuthSessionRepository sessionRepo = mock(AuthSessionRepository.class);

    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuthServiceImpl(userRepo, passwordHasher, jwtToken, versionStore, loginGuard, sessionRepo);
    }

    // ==================== Replay Detection ====================

    @Test
    void refreshReplayDetectedDeletesSession() {
        // consumeRefresh 返回 null + findUsedRefreshSid 有值 = 旧 Token 重放
        when(sessionRepo.consumeRefresh(anyString())).thenReturn(null);
        when(sessionRepo.findUsedRefreshSid(anyString())).thenReturn("sid-old");

        BizException ex = assertThrows(BizException.class, () -> service.refresh("old-refresh-token"));

        assert ex.getMessage().contains("已失效或已被使用") : ex.getMessage();
        verify(sessionRepo).delete("sid-old");
    }

    @Test
    void refreshTokenNotFoundWithoutSidNoop() {
        // consumeRefresh 返回 null + findUsedRefreshSid 也 null = token 从未有效过
        when(sessionRepo.consumeRefresh(anyString())).thenReturn(null);
        when(sessionRepo.findUsedRefreshSid(anyString())).thenReturn(null);

        assertThrows(BizException.class, () -> service.refresh("ghost-token"));

        verify(sessionRepo, never()).delete(anyString());
    }

    // ==================== Version Mismatch ====================

    @Test
    void refreshVersionMismatchDeletesSession() {
        // 模拟存储的 refresh token 值：userId:tokenVersion:sid
        // version 已 bump（比如改密码后），旧 token 的 version 不匹配
        when(sessionRepo.consumeRefresh(anyString())).thenReturn("100:5:sid-abc");
        when(versionStore.currentVersion(100L)).thenReturn(6L); // bump 后版本不匹配

        BizException ex = assertThrows(BizException.class, () -> service.refresh("stale-token"));

        assert ex.getMessage().contains("版本已失效") : ex.getMessage();
        verify(sessionRepo).delete("sid-abc");
    }

    // ==================== Happy Path ====================

    @Test
    void refreshHappyPathRotatesAndMarksUsed() {
        when(sessionRepo.consumeRefresh(anyString())).thenReturn("100:5:sid-abc");
        when(versionStore.currentVersion(100L)).thenReturn(5L);
        AuthSession session = new AuthSession();
        session.setSid("sid-abc");
        session.setUserId(100L);
        when(sessionRepo.find("sid-abc")).thenReturn(session);
        UserAccount user = new UserAccount();
        user.setId(100L);
        user.setUsername("alice");
        user.setRole("ROLE_USER");
        user.setUserStatus("ENABLED");
        when(userRepo.findById(100L)).thenReturn(user);
        when(jwtToken.issue(anyLong(), anyString(), anyString(), anyLong(), anyString())).thenReturn("new-access");
        when(jwtToken.getAccessTtlSeconds()).thenReturn(7200L);

        service.refresh("good-refresh-token");

        // 验证轮换三步
        verify(sessionRepo).rotate(eq("sid-abc"), anyString(), anyLong(), anyLong());
        verify(sessionRepo).saveRefresh(anyString(), eq(100L), eq(5L), eq("sid-abc"), anyLong());
        verify(sessionRepo).markRefreshUsed(anyString(), eq("sid-abc"), anyLong());
    }

    // ==================== 空参数 ====================

    @Test
    void refreshBlankTokenRejects() {
        assertThrows(BizException.class, () -> service.refresh(null));
        assertThrows(BizException.class, () -> service.refresh(" "));
    }

    // ==================== 格式错误 ====================

    @Test
    void refreshCorruptedStoredFormatRejects() {
        // parts.length != 3
        when(sessionRepo.consumeRefresh(anyString())).thenReturn("bad:format");

        assertThrows(BizException.class, () -> service.refresh("weird-token"));
    }
}
