package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.domain.mq.ConsumerDedupeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * 跨上下文消费去重实现：唯一键 (consumer, event_id) 兜底并发重复投递。
 */
@Service
public class ConsumerDedupeServiceImpl implements ConsumerDedupeService {

    private final ConsumerDedupeMapper consumerDedupeMapper;

    public ConsumerDedupeServiceImpl(ConsumerDedupeMapper consumerDedupeMapper) {
        this.consumerDedupeMapper = consumerDedupeMapper;
    }

    @Override
    public boolean isConsumed(String consumer, String eventId) {
        return consumerDedupeMapper.selectCount(new LambdaQueryWrapper<ConsumerDedupePO>()
            .eq(ConsumerDedupePO::getConsumer, consumer)
            .eq(ConsumerDedupePO::getEventId, eventId)) > 0;
    }

    @Override
    public void markConsumed(String consumer, String eventId) {
        ConsumerDedupePO po = new ConsumerDedupePO();
        po.setConsumer(consumer);
        po.setEventId(eventId);
        try {
            consumerDedupeMapper.insert(po);
        } catch (DuplicateKeyException e) {
            // 并发重复投递：唯一键已存在，视为已消费
        }
    }
}
