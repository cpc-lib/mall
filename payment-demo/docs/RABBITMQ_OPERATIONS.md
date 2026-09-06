# RabbitMQ Deployment And Operations Checklist

> Updated: 2026-09-06

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
    expire-minutes: 3 # local order unpaid timeout in minutes; also drives the delay queue TTL
  refund:
    status-sync-delay-ms: 60000
```

Consumer reliability (Spring AMQP listener, applies to all `@RabbitListener` consumers):

- Retry with exponential backoff: `initial-interval=2000, multiplier=3.0, max-interval=20000, max-attempts=4` (2s → 6s → 18s).
- `default-requeue-rejected: false`: after retries are exhausted the message is rejected (not requeued) — no infinite hot retry.
- Consumer ack: `acknowledge-mode: auto` — ack on listener success, nack on exception per the retry policy.
- Rejected/dropped messages are safe: DB-driven backstop jobs reconcile independently of MQ —
  `TimeoutOrderCloseScheduler` (unpaid order close, every 60s) and `RefundStatusSyncScheduler` (PROCESSING refunds, every 60s).
  Both are idempotent (status CAS + distributed locks + bizNo unique keys).
- Publisher reliability: `publisher-confirm-type: correlated` + `publisher-returns: true` + `template.mandatory: true`;
  confirm/return failures are logged (with orderNo/refundNo correlation) by `RabbitReliabilityConfig` and reconciled by the same backstop jobs.
- Transactional outbox (`t_local_message`, see `LocalMessageServiceImpl`): order-close / refund-sync messages are inserted
  as `PENDING` inside the business transaction, published after commit, marked `SENT` only after the broker confirm arrives,
  and written back `CONSUMED` by the consumer after the listener succeeds (i.e. right after Spring acks to the broker).
  Delivery retries use exponential backoff (3^n seconds, max 5 attempts) then `FAILED` for manual compensation;
  `LocalMessageSendJob` rescans `PENDING` every 30s. Manual compensation: fix the fault, then set `status='PENDING'`,
  `next_retry_time=CURRENT_TIMESTAMP` for `FAILED` rows — redelivery is safe (consumers are idempotent).
- Durability: exchanges and queues are declared durable; Spring AMQP messages default to PERSISTENT delivery mode.
- Manual compensation endpoints: `POST /api/admin/order/{orderNo}/force-close` (admin "强制关单" button) and the admin refund status query (triggers channel sync).

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
| queue | `payment.order.close.delay.queue` | Durable delay queue. TTL: `payment.order.expire-minutes * 60000`. NOTE: queue args are immutable in RabbitMQ — after changing `expire-minutes`, delete this queue once and let the backend redeclare it. |
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
