package cc.ivera.shared.domain.mq;

/**
 * 跨上下文消费去重端口：同一 consumer 对同一 eventId 只处理一次。
 */
public interface ConsumerDedupeService {

    boolean isConsumed(String consumer, String eventId);

    void markConsumed(String consumer, String eventId);
}
