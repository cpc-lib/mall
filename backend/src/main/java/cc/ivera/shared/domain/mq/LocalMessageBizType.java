package cc.ivera.shared.domain.mq;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 本地消息业务类型元数据：收敛每种消息的负载类型、交换机与延迟路由键。
 *
 * <p>交换机/路由键字面量与各域 RabbitConfig 中的 Bean 声明保持一致
 * （OrderCloseRabbitConfig / RefundStatusSyncRabbitConfig）。</p>
 */
public enum LocalMessageBizType {

    ORDER_CLOSE(
        "ORDER_CLOSE",
        OrderCloseMessage.class,
        "payment.order.close.event.exchange",
        "payment.order.close.delay"
    ),

    REFUND_SYNC(
        "REFUND_SYNC",
        RefundStatusSyncMessage.class,
        "payment.refund.status-sync.event.exchange",
        "payment.refund.status-sync.delay"
    );

    private static final Map<String, LocalMessageBizType> BY_TYPE = Collections.unmodifiableMap(
        Arrays.stream(values()).collect(Collectors.toMap(LocalMessageBizType::getType, t -> t))
    );
    private final String type;
    private final Class<?> payloadClass;
    private final String exchange;
    private final String routingKey;

    LocalMessageBizType(String type, Class<?> payloadClass, String exchange, String routingKey) {
        this.type = type;
        this.payloadClass = payloadClass;
        this.exchange = exchange;
        this.routingKey = routingKey;
    }

    public static LocalMessageBizType of(String type) {
        LocalMessageBizType bizType = BY_TYPE.get(type);
        if (bizType == null) {
            throw new IllegalArgumentException("未知的本地消息业务类型：" + type);
        }
        return bizType;
    }

    public String getType() {
        return type;
    }

    public Class<?> getPayloadClass() {
        return payloadClass;
    }

    public String getExchange() {
        return exchange;
    }

    public String getRoutingKey() {
        return routingKey;
    }
}
