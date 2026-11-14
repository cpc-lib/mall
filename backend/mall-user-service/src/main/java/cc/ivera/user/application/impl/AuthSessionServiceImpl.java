package cc.ivera.user.application.impl;

import cc.ivera.shared.domain.exception.BizException;
import cc.ivera.user.application.AuthSessionService;
import cc.ivera.user.domain.model.AuthSession;
import cc.ivera.user.domain.repository.AuthSessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthSessionServiceImpl implements AuthSessionService {

    private final AuthSessionRepository authSessionRepository;

    public AuthSessionServiceImpl(AuthSessionRepository authSessionRepository) {
        this.authSessionRepository = authSessionRepository;
    }

    @Override
    public List<AuthSession> listSessionsByUser(Long userId) {
        return authSessionRepository.listByUser(userId);
    }

    @Override
    public void kick(Long userId, String sid) {
        AuthSession session = authSessionRepository.find(sid);
        // 只允许踢掉该用户自己的会话，避免越权下线他人
        if (session == null || !userId.equals(session.getUserId())) throw new BizException("会话不存在");
        authSessionRepository.delete(sid);
    }

    @Override
    public void kickAll(Long userId) {
        authSessionRepository.deleteAll(userId);
    }
}
