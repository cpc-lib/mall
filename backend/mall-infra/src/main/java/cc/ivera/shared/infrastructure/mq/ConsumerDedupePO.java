package cc.ivera.shared.infrastructure.mq;

import cc.ivera.shared.infrastructure.po.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 跨上下文消费去重 PO：t_consumer_dedupe。
 */
@Data
@TableName("t_consumer_dedupe")
public class ConsumerDedupePO extends BaseEntity {

    private String consumer;//消费者标识
    private String eventId;//事件唯一标识
}
