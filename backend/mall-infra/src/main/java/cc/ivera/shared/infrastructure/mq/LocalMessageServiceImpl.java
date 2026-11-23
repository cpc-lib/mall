package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.mq.LocalMessageBizType;
import cc.ivera.shared.domain.mq.LocalMessageService;
import cc.ivera.shared.infrastructure.util.JsonUtils;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationAdapter;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@ConditionalOnClass(RabbitTemplate.class)
@Slf4j
public class LocalMessageServiceImpl implements LocalMessageService {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int SCAN_LIMIT = 100;
    private static final long BROKER_CONFIRM_TIMEOUT_MS = 5000;
    /**
     * 初次落库后预留的异步投递宽限期：兜底扫描只捞 next_retry_time &lt;= now 的记录，
     * 初次落库即到期的行会被扫描线程与 afterCommit 首次投递并发重投。
     */
    private static final long INITIAL_PUBLISH_GRACE_MS = 10_000L;

    private final LocalMessageMapper localMessageMapper;

    private final RabbitTemplate rabbitTemplate;

    public LocalMessageServiceImpl(LocalMessageMapper localMessageMapper, RabbitTemplate rabbitTemplate) {
        this.localMessageMapper = localMessageMapper;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Override
    public void saveAndPublishAfterCommit(String bizType, String bizNo, Object payload) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizNo)) {
            throw new IllegalArgumentException("本地消息落库失败，bizType 或 bizNo 为空");
        }

        LocalMessage message = new LocalMessage();
        message.setBizType(bizType);
        message.setBizNo(bizNo);
        message.setMessageContent(JsonUtils.toJson(payload));
        message.setStatus(LocalMessage.STATUS_PENDING);
        message.setRetryCount(0);
        // 预留宽限期给 afterCommit 首次异步投递，避免兜底扫描抢在同一时刻重投造成重复消息。
        message.setNextRetryTime(new Date(System.currentTimeMillis() + INITIAL_PUBLISH_GRACE_MS));
        // 有事务则随业务事务一起提交（发件箱与业务原子），无事务立即提交
        localMessageMapper.insert(message);

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 与业务同事务：等提交后再投递，避免消费者先于事务提交收到消息
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronizationAdapter() {
                @Override
                public void afterCommit() {
                    publishOne(message);
                }
            });
            log.info("本地消息已随业务事务落库，提交后投递，bizType={}, bizNo={}", bizType, bizNo);
        } else {
            publishOne(message);
        }
    }

    @Override
    public void publishPending() {
        List<LocalMessage> messages = localMessageMapper.selectPendingMessages(
            LocalMessage.STATUS_PENDING, new Date(), SCAN_LIMIT);
        for (LocalMessage message : messages) {
            publishOne(message);
        }
        if (!messages.isEmpty()) {
            log.info("本地消息表兜底扫描完成，本轮处理 {} 条待投递消息", messages.size());
        }
    }

    @Override
    public void markConsumed(String bizType, String bizNo) {
        if (!StringUtils.hasText(bizType) || !StringUtils.hasText(bizNo)) {
            return;
        }
        // 仅 PENDING/SENT 可流转为 CONSUMED：幂等，且避免覆盖（发件箱定时重试可能与快速消费竞态）
        LambdaUpdateWrapper<LocalMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LocalMessage::getBizType, bizType)
            .eq(LocalMessage::getBizNo, bizNo)
            .in(LocalMessage::getStatus, LocalMessage.STATUS_PENDING, LocalMessage.STATUS_SENT)
            .set(LocalMessage::getStatus, LocalMessage.STATUS_CONSUMED)
            .set(LocalMessage::getUpdateTime, new Date());
        int updated = localMessageMapper.update(null, updateWrapper);
        if (updated > 0) {
            log.info("本地消息消费成功回写 CONSUMED，bizType={}, bizNo={}", bizType, bizNo);
        } else {
            log.debug("本地消息消费回写无匹配行（可能为旧消息或已回写），bizType={}, bizNo={}", bizType, bizNo);
        }
    }

    /**
     * 投递单条消息：异步等待发送者确认（broker ack）后再置 SENT，否则排期重试。
     * 不在业务 afterCommit / HTTP 线程上同步阻塞等待 Confirm。
     */
    private void publishOne(LocalMessage message) {
        try {
            LocalMessageBizType bizType = LocalMessageBizType.of(message.getBizType());
            CorrelationData correlationData = new CorrelationData(message.getBizNo());
            rabbitTemplate.convertAndSend(
                bizType.getExchange(),
                bizType.getRoutingKey(),
                deserializePayload(message, bizType),
                correlationData);
            // orTimeout 保留原有 5 秒确认超时语义：超时同样进入指数回避重试。
            correlationData.getFuture()
                .orTimeout(BROKER_CONFIRM_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .whenCompleteAsync((confirm, throwable) -> {
                    if (throwable == null && confirm != null && confirm.isAck()) {
                        markSent(message.getId());
                    } else {
                        scheduleRetry(message, throwable != null ? throwable.getMessage() : "发送者确认失败");
                    }
                });
        } catch (Exception e) {
            scheduleRetry(message, e.getMessage());
        }
    }

    /**
     * CAS 置 SENT：仅 PENDING 可流转，避免覆盖快速消费者已回写的 CONSUMED。
     */
    private void markSent(Long id) {
        LambdaUpdateWrapper<LocalMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LocalMessage::getId, id)
            .eq(LocalMessage::getStatus, LocalMessage.STATUS_PENDING)
            .set(LocalMessage::getStatus, LocalMessage.STATUS_SENT)
            .set(LocalMessage::getUpdateTime, new Date());
        localMessageMapper.update(null, updateWrapper);
    }

    /**
     * 投递失败排期重试：指数回避 3^n 秒（3s→9s→27s→81s→243s），超限置 FAILED 待人工补偿。
     * 仅 PENDING 可被排期：若并发线程已确认并置 SENT（或已被消费置 CONSUMED），此处必须放弃回写，
     * 否则会把 SENT/CONSUMED 覆盖回 PENDING/FAILED 造成重复投递。
     */
    private void scheduleRetry(LocalMessage message, String reason) {
        int nextRetryCount = (message.getRetryCount() == null ? 0 : message.getRetryCount()) + 1;
        Date now = new Date();
        LambdaUpdateWrapper<LocalMessage> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(LocalMessage::getId, message.getId())
            .eq(LocalMessage::getStatus, LocalMessage.STATUS_PENDING)
            .set(LocalMessage::getRetryCount, nextRetryCount)
            .set(LocalMessage::getUpdateTime, now);
        if (nextRetryCount > MAX_RETRY_COUNT) {
            updateWrapper.set(LocalMessage::getStatus, LocalMessage.STATUS_FAILED);
            log.error("本地消息投递重试超限，置为 FAILED 待人工补偿，bizType={}, bizNo={}, reason={}",
                message.getBizType(), message.getBizNo(), reason);
        } else {
            long delayMs = (long) Math.pow(3, nextRetryCount) * 1000;
            updateWrapper.set(LocalMessage::getStatus, LocalMessage.STATUS_PENDING)
                .set(LocalMessage::getNextRetryTime, new Date(now.getTime() + delayMs));
            log.warn("本地消息投递失败，第{}次重试已排期，bizType={}, bizNo={}, reason={}",
                nextRetryCount, message.getBizType(), message.getBizNo(), reason);
        }
        localMessageMapper.update(null, updateWrapper);
    }

    /**
     * 从落库 JSON 还原消息体：MQ 线上格式为 POJO（SimpleMessageConverter Java 序列化），
     * 兜底重投必须还原为原始类型，保证消费者反序列化兼容。
     */
    private Object deserializePayload(LocalMessage message, LocalMessageBizType bizType) {
        return JsonUtils.toObject(message.getMessageContent(), bizType.getPayloadClass());
    }
}
