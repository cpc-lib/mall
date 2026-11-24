package cc.ivera.shared.infrastructure.lock;

import cc.ivera.shared.domain.lock.LockAuditRecorder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBatch;
import org.redisson.api.RStreamAsync;
import org.redisson.api.RedissonClient;
import org.redisson.api.stream.StreamAddArgs;
import org.redisson.client.codec.StringCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基于 Redis Stream 的锁审计记录：每次加锁/释放/获取失败追加一条事件，
 * 用 {@code MAXLEN} 与 TTL 双重限制体积，不落库、不参与业务判断。
 * <p>
 * 审计写入失败只记录 WARN，不影响临界区执行与锁释放。
 */
@Slf4j
@Component
@ConditionalOnClass(RedissonClient.class)
public class RedisLockAuditRecorder implements LockAuditRecorder {

    private static final String STREAM_KEY = "lock:audit";

    private static final int MAX_LENGTH = 1000;

    private static final Duration TTL = Duration.ofDays(7);

    private final RedissonClient redissonClient;

    public RedisLockAuditRecorder(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public void record(String event, String lockKey, Long fenceToken, long heldMillis) {
        try {
            Map<String, String> fields = new LinkedHashMap<>();
            fields.put("ts", String.valueOf(System.currentTimeMillis()));
            fields.put("event", event);
            fields.put("lockKey", lockKey);
            fields.put("fenceToken", fenceToken == null ? "" : String.valueOf(fenceToken));
            fields.put("heldMs", String.valueOf(heldMillis));
            fields.put("thread", Thread.currentThread().getName());

            append(fields);
        } catch (RuntimeException e) {
            log.warn("分布式锁审计写入失败，event={}，lockKey={}", event, lockKey, e);
        }
    }

    /**
     * 单次批量管道完成 XADD（含 MAXLEN ~ 裁剪）与 TTL 续期，避免 3 次串行 RTT。
     */
    private void append(Map<String, String> fields) {
        RBatch batch = redissonClient.createBatch();
        RStreamAsync<String, String> stream = batch.getStream(STREAM_KEY, StringCodec.INSTANCE);
        stream.addAsync(StreamAddArgs.entries(fields).trimNonStrict().maxLen(MAX_LENGTH).noLimit());
        stream.expireAsync(TTL);
        batch.execute();
    }
}
