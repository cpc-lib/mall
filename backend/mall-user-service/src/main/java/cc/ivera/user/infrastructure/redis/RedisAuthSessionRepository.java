package cc.ivera.user.infrastructure.redis;

import cc.ivera.user.domain.model.AuthSession;
import cc.ivera.user.domain.repository.AuthSessionRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis 会话注册表：
 * auth:session:{sid}          会话 hash（uid/refreshHash/issuedAt/lastSeenAt/ip/ua）
 * auth:user_sessions:{uid}    用户会话索引 Set&lt;sid&gt;
 * auth:refresh:{hash}         Refresh Token（只存 SHA-256）
 * auth:refresh_used:{hash}    已使用的 Refresh Token → sid，用于重放检测
 */
@Repository
public class RedisAuthSessionRepository implements AuthSessionRepository {

    private static final String SESSION_PREFIX = "auth:session:";
    private static final String USER_SESSIONS_PREFIX = "auth:user_sessions:";
    private static final String REFRESH_PREFIX = "auth:refresh:";
    private static final String REFRESH_USED_PREFIX = "auth:refresh_used:";
    private static final DefaultRedisScript<String> CONSUME_REFRESH_SCRIPT =
        new DefaultRedisScript<>(
            "local v=redis.call('GET',KEYS[1]); if v then redis.call('DEL',KEYS[1]); end; return v",
            String.class);

    private final StringRedisTemplate redisTemplate;

    public RedisAuthSessionRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void create(AuthSession session, long ttlSeconds) {
        Map<String, String> values = new HashMap<>();
        values.put("uid", String.valueOf(session.getUserId()));
        values.put("refreshHash", nullSafe(session.getRefreshHash()));
        values.put("issuedAt", String.valueOf(session.getIssuedAt()));
        values.put("lastSeenAt", String.valueOf(session.getLastSeenAt()));
        values.put("ip", nullSafe(session.getIp()));
        values.put("ua", nullSafe(session.getUserAgent()));

        String key = SESSION_PREFIX + session.getSid();
        redisTemplate.opsForHash().putAll(key, values);
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);

        String indexKey = USER_SESSIONS_PREFIX + session.getUserId();
        redisTemplate.opsForSet().add(indexKey, session.getSid());
        redisTemplate.expire(indexKey, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public AuthSession find(String sid) {
        if (sid == null || sid.isEmpty()) return null;
        Map<Object, Object> values = redisTemplate.opsForHash().entries(SESSION_PREFIX + sid);
        if (values == null || values.isEmpty()) return null;
        AuthSession session = new AuthSession();
        session.setSid(sid);
        session.setUserId(Long.valueOf(String.valueOf(values.get("uid"))));
        session.setRefreshHash(stringValue(values.get("refreshHash")));
        session.setIssuedAt(longValue(values.get("issuedAt")));
        session.setLastSeenAt(longValue(values.get("lastSeenAt")));
        session.setIp(stringValue(values.get("ip")));
        session.setUserAgent(stringValue(values.get("ua")));
        return session;
    }

    @Override
    public List<AuthSession> listByUser(Long userId) {
        Set<String> sids = redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + userId);
        if (sids == null || sids.isEmpty()) return Collections.emptyList();
        List<AuthSession> result = new ArrayList<>();
        for (String sid : sids) {
            AuthSession session = find(sid);
            if (session != null && userId.equals(session.getUserId())) result.add(session);
        }
        return result;
    }

    @Override
    public void rotate(String sid, String refreshHash, long now, long ttlSeconds) {
        String key = SESSION_PREFIX + sid;
        redisTemplate.opsForHash().put(key, "refreshHash", nullSafe(refreshHash));
        redisTemplate.opsForHash().put(key, "lastSeenAt", String.valueOf(now));
        redisTemplate.expire(key, ttlSeconds, TimeUnit.SECONDS);
        AuthSession session = find(sid);
        if (session != null) {
            String indexKey = USER_SESSIONS_PREFIX + session.getUserId();
            redisTemplate.expire(indexKey, ttlSeconds, TimeUnit.SECONDS);
        }
    }

    @Override
    public void delete(String sid) {
        AuthSession session = find(sid);
        redisTemplate.delete(SESSION_PREFIX + sid);
        if (session == null) return;
        redisTemplate.opsForSet().remove(USER_SESSIONS_PREFIX + session.getUserId(), sid);
        String refreshHash = session.getRefreshHash();
        if (refreshHash != null && !refreshHash.isEmpty()) redisTemplate.delete(REFRESH_PREFIX + refreshHash);
    }

    @Override
    public void deleteAll(Long userId) {
        Set<String> sids = redisTemplate.opsForSet().members(USER_SESSIONS_PREFIX + userId);
        if (sids != null) {
            for (String sid : sids) delete(sid);
        }
        redisTemplate.delete(USER_SESSIONS_PREFIX + userId);
    }

    @Override
    public void saveRefresh(String tokenHash, Long userId, long tokenVersion, String sid, long ttlSeconds) {
        redisTemplate.opsForValue().set(REFRESH_PREFIX + tokenHash, userId + ":" + tokenVersion + ":" + sid, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public String consumeRefresh(String tokenHash) {
        return redisTemplate.execute(CONSUME_REFRESH_SCRIPT, Collections.singletonList(REFRESH_PREFIX + tokenHash));
    }

    @Override
    public String findUsedRefreshSid(String tokenHash) {
        return redisTemplate.opsForValue().get(REFRESH_USED_PREFIX + tokenHash);
    }

    @Override
    public void markRefreshUsed(String tokenHash, String sid, long ttlSeconds) {
        redisTemplate.opsForValue().set(REFRESH_USED_PREFIX + tokenHash, sid, ttlSeconds, TimeUnit.SECONDS);
    }

    @Override
    public void deleteRefresh(String tokenHash) {
        redisTemplate.delete(REFRESH_PREFIX + tokenHash);
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long longValue(Object value) {
        if (value == null) return 0L;
        String text = String.valueOf(value);
        return text.isEmpty() ? 0L : Long.parseLong(text);
    }
}
