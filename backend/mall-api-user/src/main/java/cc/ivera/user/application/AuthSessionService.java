package cc.ivera.user.application;

import cc.ivera.user.domain.model.AuthSession;

import java.util.List;

/**
 * 会话治理：管理员按用户查看在线设备并精细下线。
 */
public interface AuthSessionService {

    List<AuthSession> listSessionsByUser(Long userId);

    void kick(Long userId, String sid);

    void kickAll(Long userId);
}
