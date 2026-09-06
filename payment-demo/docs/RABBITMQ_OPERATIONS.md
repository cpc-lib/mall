# RabbitMQ Deployment And Operations Checklist

> Updated: 2026-06-09

This project declares RabbitMQ topology through Spring AMQP configuration classes at application startup. Operations still need to verify that the broker, permissions, queues, exchanges, bindings, and delay settings match the runtime environment before enabling payment/refund traffic.

## Runtime Connection

Configured in `src/main/resources/application.yml`:

```yaml
spring:
  rabbitmq:
    host: your-rabbitmq-host
    port: 5672
    username: guest
    password: guest

payment:
  order:
    close-delay-ms: 60000
  refund:
    status-sync-delay-ms: 60000
```

Checklist:

- [ ] RabbitMQ host and port are reachable from the backend runtime.
- [ ] The configured user has permission to declare exchanges, queues, and bindings in the target vhost.
- [ ] The configured user has publish permission for `payment.*.event.exchange`.
- [ ] The configured user has consume permission for `payment.*.release.queue`.
- [ ] Delay values are reviewed for the deployment environment.

## Declared Topology

### Delayed Order Close

Declared by `cc.ivera.config.OrderCloseRabbitConfig`.

| Type | Name | Notes |
|---|---|---|
| exchange | `payment.order.close.event.exchange` | Producer publishes delayed close messages here. |
| exchange | `payment.order.close.dead-letter.exchange` | Receives expired delay queue messages. |
| queue | `payment.order.close.delay.queue` | Durable delay queue. TTL: `payment.order.close-delay-ms`. |
| queue | `payment.order.close.release.queue` | Durable release queue consumed by `OrderCloseConsumer`. |
| routing key | `payment.order.close.delay` | Event exchange to delay queue. |
| routing key | `payment.order.close.release` | Dead-letter exchange to release queue. |

### Refund Status Sync

Declared by `cc.ivera.config.RefundStatusSyncRabbitConfig`.

| Type | Name | Notes |
|---|---|---|
| exchange | `payment.refund.status-sync.event.exchange` | Producer publishes delayed refund status sync messages here. |
| exchange | `payment.refund.status-sync.dead-letter.exchange` | Receives expired delay queue messages. |
| queue | `payment.refund.status-sync.delay.queue` | Durable delay queue. TTL: `payment.refund.status-sync-delay-ms`. |
| queue | `payment.refund.status-sync.release.queue` | Durable release queue consumed by `RefundStatusSyncConsumer`. |
| routing key | `payment.refund.status-sync.delay` | Event exchange to delay queue. |
| routing key | `payment.refund.status-sync.release` | Dead-letter exchange to release queue. |

## Pre-Deployment Checks

- [ ] Start the backend in a non-production environment and confirm Spring AMQP declares the topology without permission errors.
- [ ] Verify all exchanges are durable direct exchanges.
- [ ] Verify delay queues contain `x-dead-letter-exchange`, `x-dead-letter-routing-key`, and `x-message-ttl`.
- [ ] Verify release queues have active consumers after the backend starts.
- [ ] Confirm no stale queue with incompatible arguments already exists. RabbitMQ rejects redeclaration when queue arguments differ.

## Smoke Test

1. Approve a refund in a non-production environment.
2. Confirm a message reaches `payment.refund.status-sync.delay.queue`.
3. Wait for `payment.refund.status-sync-delay-ms`.
4. Confirm the message moves to `payment.refund.status-sync.release.queue` and is consumed.
5. Confirm application logs contain:

```text
Refund status sync message sent
Start refund status sync message
Finished refund status sync message
```

## Rollback Notes

- Rolling back the application stops producing and consuming refund status sync messages.
- Existing delayed messages may remain in RabbitMQ until consumed, expired, purged, or deleted by operations.
- Do not delete order-close queues when rolling back refund status sync; they are separate topologies.
