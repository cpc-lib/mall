package cc.ivera.lock;

import cc.ivera.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Component
@Slf4j
public class RedissonDistributedLockTemplate implements DistributedLockTemplate {

    private final RedissonClient redissonClient;

    public RedissonDistributedLockTemplate(RedissonClient redissonClient) {
        this.redissonClient = redissonClient;
    }

    @Override
    public <T> T execute(String lockKey, long waitTimeMillis, long leaseTimeMillis, Supplier<T> supplier) {
        if (lockKey == null || lockKey.trim().isEmpty()) {
            throw new BizException("分布式锁key不能为空");
        }
        if (supplier == null) {
            throw new BizException("分布式锁业务逻辑不能为空");
        }

        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;

        try {
            if (leaseTimeMillis > 0) {
                locked = lock.tryLock(waitTimeMillis, leaseTimeMillis, TimeUnit.MILLISECONDS);
            } else {
                locked = lock.tryLock(waitTimeMillis, -1, TimeUnit.MILLISECONDS);
            }

            if (!locked) {
                log.warn("获取分布式锁失败，lockKey={}", lockKey);
                throw new BizException("系统繁忙，请勿重复提交");
            }

            log.debug("获取分布式锁成功，lockKey={}", lockKey);
            return supplier.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("获取分布式锁被中断，lockKey={}", lockKey, e);
            throw new BizException("获取分布式锁被中断", e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("释放分布式锁成功，lockKey={}", lockKey);
            }
        }
    }
}
