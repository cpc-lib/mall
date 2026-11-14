package cc.ivera.user.domain.repository;

import cc.ivera.user.domain.model.AuthSession;

import java.util.List;

/**
 * 会话注册表端口：会话记录、用户会话索引与 Refresh Token 一次性轮换。
 * 实现位于 infrastructure，领域/应用层只依赖本接口。
 */
public interface AuthSessionRepository {

    void create(AuthSession session, long ttlSeconds);

    AuthSession find(String sid);

    List<AuthSession> listByUser(Long userId);

    /**
     * Refresh Token 轮换：更新会话内的 refresh 摘要并续期。
     */
    void rotate(String sid, String refreshHash, long now, long ttlSeconds);

    /**
     * 吊销单个会话：删除会话记录、用户索引与对应 Refresh Token。
     */
    void delete(String sid);

    void deleteAll(Long userId);

    void saveRefresh(String tokenHash, Long userId, long tokenVersion, String sid, long ttlSeconds);

    /**
     * 原子取用 Refresh Token（GET + DEL），值格式 uid:ver:sid；不存在返回 null。
     */
    String consumeRefresh(String tokenHash);

    /**
     * 已使用过的 Refresh Token 对应的 sid；用于识别重放。
     */
    String findUsedRefreshSid(String tokenHash);

    void markRefreshUsed(String tokenHash, String sid, long ttlSeconds);

    void deleteRefresh(String tokenHash);
}
