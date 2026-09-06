package cc.ivera.service;

/**
 * 本地消息表（事务性发件箱）服务。
 *
 * <p>状态流转：PENDING（业务事务内落库）→ SENT（事务提交后投递 MQ 且发送者确认到达）
 * → CONSUMED（消费者监听器成功返回后回写）；投递重试超限 → FAILED（人工补偿）。</p>
 */
public interface LocalMessageService {

    /**
     * 业务事务内落库消息并安排投递：
     * - 存在事务：消息与业务变更同事务落库，事务提交后再投递 MQ（避免消费者读到未提交数据）；
     * - 无事务：立即落库并投递。
     */
    void saveAndPublishAfterCommit(String bizType, String bizNo, Object payload);

    /**
     * 定时兜底：扫描 PENDING 且到达重试时间的消息重新投递（指数回避，重试超限置 FAILED）。
     */
    void publishPending();

    /**
     * 消费成功回调：消费者监听器成功处理后回写 CONSUMED（幂等，仅 PENDING/SENT 可流转）。
     */
    void markConsumed(String bizType, String bizNo);
}
