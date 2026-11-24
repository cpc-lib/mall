package cc.ivera.user.infrastructure.redis;

import cc.ivera.user.domain.gateway.TokenVersionPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 基于 Redis 的 Access Token 全局版本号存储。
 */
@Component
public class RedisTokenVersionStore implements TokenVersionPort {
    private static final String VERSION_PREFIX = "auth:token_version:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenVersionStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public long currentVersion(Long userId) {
        // key 不存在时默认版本号即为 0，无需先 SETNX 再 GET
        String value = redisTemplate.opsForValue().get(VERSION_PREFIX + userId);
        return value == null ? 0L : Long.parseLong(value);
    }

    @Override
    public void bumpVersion(Long userId) {
        if (userId == null) return;
        redisTemplate.opsForValue().increment(VERSION_PREFIX + userId);
    }
}
