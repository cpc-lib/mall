# RabbitMQ Deployment And Operations Checklist

> Updated: 2026-09-10

This project declares RabbitMQ topology through Spring AMQP configuration classes at application startup. Operations
still need to verify that the broker, permissions, queues, exchanges, bindings, and delay settings match the runtime
environment before enabling payment/refund traffic.

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
    timeout-scan-ms: 60000 # DB backstop scan interval for timed-out NOTPAY orders
  refund:
    status-sync-delay-ms: 60000 # MQ delay before the first refund status sync
    status-sync-scan-ms: 60000 # DB backstop scan interval for APPROVED + PROCESSING refunds
```

Consumer reliability (Spring AMQP listener, applies to all `@RabbitListener` consumers):

- Retry with exponential backoff: `initial-interval=2000, multiplier=3.0, max-interval=20000, max-attempts=4` (2s → 6s →
  18s).
- `default-requeue-rejected: false`: after retries are exhausted the message is rejected (not requeued) — no infinite
  hot retry. Both release queues declare a failure DLX, so the broker dead-letters the rejected message into the matching
  `*.failure.queue` instead of silently dropping it.
- Consumer ack: `acknowledge-mode: manual` — `OrderCloseConsumer` and `RefundStatusSyncConsumer` call
  `Channel.basicAck(deliveryTag, false)` only after business processing and `markConsumed` both succeed. Business / outbox
  update / ack exceptions are not swallowed, so Spring Retry still owns retry/reject behavior. Do not `basicNack` on the
  first business failure; doing so would end the broker delivery before the configured retry policy is exhausted.
- `*.failure.queue` is the first operational quarantine for exhausted messages. Inspect `x-death`, fix the root cause, and
  either manually replay the original message or route it to the matching `*.parking-lot.queue` for long-term isolation.
  Parking-lot queues have no consumers and no automatic route back to the release queue, preventing poison-message loops.
- DB-driven backstop jobs reconcile business state independently of MQ. `TimeoutOrderCloseScheduler` scans timed-out
  `NOTPAY` orders on `payment.order.timeout-scan-ms` and reuses the existing channel-query/close/CAS path;
  `RefundStatusSyncScheduler` scans `APPROVED + PROCESSING` refunds on `payment.refund.status-sync-scan-ms` and reuses
  `queryRefundStatus`. One failed record is logged and skipped so later records in the same batch still converge. Both paths
  remain idempotent through the existing distributed locks, CAS transitions, and bizNo/unique-key guards. Failure queues are
  therefore an observability/operations layer rather than the only recovery path.
- Publisher reliability: `publisher-confirm-type: correlated` + `publisher-returns: true` + `template.mandatory: true`;
  confirm/return failures are logged (with orderNo/refundNo correlation) by `RabbitReliabilityConfig` and reconciled by
  the same backstop jobs.
- Transactional outbox (`t_local_message`, see `LocalMessageServiceImpl`): order-close / refund-sync messages are
  inserted
  as `PENDING` inside the business transaction, published after commit, marked `SENT` only after the broker confirm
  arrives,
  and written back `CONSUMED` by the consumer after business processing succeeds; only then does the consumer explicitly
  call `basicAck(deliveryTag, false)`. If business processing or the CONSUMED write-back fails, no ack is sent and the
  exception continues into Spring Retry. If `basicAck` itself fails after `CONSUMED` was persisted, the exception is also
  propagated; RabbitMQ may redeliver the message, so consumers must remain idempotent. This is intentional at-least-once
  delivery semantics.
  Delivery retries use exponential backoff (3^n seconds, max 5 attempts) then `FAILED` for manual compensation;
  `LocalMessageSendJob` rescans `PENDING` every 30s. Manual compensation: fix the fault, then set `status='PENDING'`,
  `next_retry_time=CURRENT_TIMESTAMP` for `FAILED` rows — redelivery is safe (consumers are idempotent).
- Process ownership: order-close consumers, payment-event consumers, order `@Scheduled` jobs (timeout scan, logistics
  mock, auto receipt, order-request cleanup) and the order MQ topology run only in `mall-order-service`, which also relays
  its own outbox messages (`ORDER_CLOSE`, `PAYMENT_LATE_ARRIVED`). Refund reversal consumers
  (`payment.duplicate.refund.queue`, `payment.late.refund.queue`), `RefundStatusSyncConsumer` and
  `RefundStatusSyncScheduler` run only in `mall-refund-service`, which relays its own `REFUND_SYNC` outbox messages.
  `mall-payment-service` sets `spring.rabbitmq.listener.simple.auto-startup=false` (it publishes but does not consume) and
  relays its own `PAYMENT_SUCCEEDED` / `DUPLICATE_PAYMENT_DETECTED` messages; `mall-admin-bff` and `mall-bill-service` keep
  listeners and scheduling disabled. Do not duplicate order/payment consumers in another process without first removing the
  duplicate-consumer risk.
- Outbox relay is sharded by domain: every service runs `LocalMessageSendJob` with `local-message.relay-biz-types` limited to
  its own biz types, and the lock key is `scheduler:local-message-send:{spring.application.name}`. Never share one global lock
  key between services — with a shared key only one process can scan per round and the other domains starve. `mall-user-bff`,
  `mall-admin-bff` and `mall-bill-service` set `local-message.relay-enabled=false`. A publisher-confirm ack only guarantees
  arrival at the exchange; unroutable messages are logged by `RabbitReliabilityConfig` but the outbox row is still marked
  `SENT`, so every relaying service must have the target exchange declared (all services scan `cc.ivera`, so
  `PaymentEventRabbitConfig` from `mall-api-payment` provides the payment topology).
- Availability note: because each domain only relays its own messages, while a domain service is down its pending outbox rows
  wait until it recovers instead of being drained by another process.
- Durability: exchanges and queues are declared durable; Spring AMQP messages default to PERSISTENT delivery mode.
- Manual compensation endpoints: `POST /api/admin/order/{orderNo}/force-close` (admin "强制关单" button) and the admin
  refund status query (triggers channel sync).

Checklist:

- [ ] RabbitMQ host and port are reachable from the backend runtime.
- [ ] The configured user has permission to declare exchanges, queues, and bindings in the target vhost.
- [ ] The configured user has publish permission for `payment.*.event.exchange`.
- [ ] The configured user has consume permission for `payment.*.release.queue`.
- [ ] Operations can inspect/purge the `payment.*.failure.queue` and `payment.*.parking-lot.queue` queues as required by the deployment policy.
- [ ] Delay values are reviewed for the deployment environment.

## Declared Topology

### Delayed Order Close

Declared by `cc.ivera.order.infrastructure.mq.OrderCloseRabbitConfig`.

| Type        | Name                                       | Notes                                                                                                                                                                                                    |
|-------------|--------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| exchange    | `payment.order.close.event.exchange`       | Producer publishes delayed close messages here.                                                                                                                                                          |
| exchange    | `payment.order.close.dead-letter.exchange` | Receives expired delay queue messages.                                                                                                                                                                   |
| exchange    | `payment.order.close.failure.exchange`     | Receives messages rejected from the release queue after Spring Retry is exhausted.                                                                                                                       |
| queue       | `payment.order.close.delay.queue`          | Durable delay queue. TTL: `payment.order.expire-minutes * 60000`. NOTE: queue args are immutable in RabbitMQ — after changing `expire-minutes`, delete this queue once and let the backend redeclare it. |
| queue       | `payment.order.close.release.queue`        | Durable release queue consumed by `OrderCloseConsumer`; dead-letters final rejects to the failure exchange.                                                                                              |
| queue       | `payment.order.close.failure.queue`        | Durable operational failure queue. Inspect/replay manually after root-cause analysis.                                                                                                                     |
| queue       | `payment.order.close.parking-lot.queue`    | Durable long-term poison-message quarantine; no automatic replay.                                                                                                                                        |
| routing key | `payment.order.close.delay`                | Event exchange to delay queue.                                                                                                                                                                           |
| routing key | `payment.order.close.release`              | Delay dead-letter exchange to release queue.                                                                                                                                                             |
| routing key | `payment.order.close.failure`              | Failure exchange to failure queue.                                                                                                                                                                       |
| routing key | `payment.order.close.parking-lot`          | Failure exchange to parking-lot queue for manual quarantine.                                                                                                                                             |

### Refund Status Sync

Declared by `cc.ivera.refund.infrastructure.config.RefundStatusSyncRabbitConfig` (in `mall-api-refund`, consumed by
`mall-refund-service`).

| Type        | Name                                              | Notes                                                            |
|-------------|---------------------------------------------------|------------------------------------------------------------------|
| exchange    | `payment.refund.status-sync.event.exchange`       | Producer publishes delayed refund status sync messages here.     |
| exchange    | `payment.refund.status-sync.dead-letter.exchange` | Receives expired delay queue messages.                                                   |
| exchange    | `payment.refund.status-sync.failure.exchange`     | Receives messages rejected from the release queue after Spring Retry is exhausted.       |
| queue       | `payment.refund.status-sync.delay.queue`          | Durable delay queue. TTL: `payment.refund.status-sync-delay-ms`.                         |
| queue       | `payment.refund.status-sync.release.queue`        | Durable release queue consumed by `RefundStatusSyncConsumer`; final rejects go to DLX.   |
| queue       | `payment.refund.status-sync.failure.queue`        | Durable operational failure queue for inspection and controlled manual replay.           |
| queue       | `payment.refund.status-sync.parking-lot.queue`    | Durable long-term poison-message quarantine; no automatic replay.                        |
| routing key | `payment.refund.status-sync.delay`                | Event exchange to delay queue.                                                           |
| routing key | `payment.refund.status-sync.release`              | Delay dead-letter exchange to release queue.                                             |
| routing key | `payment.refund.status-sync.failure`              | Failure exchange to failure queue.                                                       |
| routing key | `payment.refund.status-sync.parking-lot`          | Failure exchange to parking-lot queue for manual quarantine.                             |

### Payment Business Events

Declared by `cc.ivera.payment.infrastructure.mq.PaymentEventRabbitConfig`.

| Type        | Name                               | Notes                                                                                     |
|-------------|------------------------------------|-------------------------------------------------------------------------------------------|
| exchange    | `payment.event.exchange`           | Producer publishes payment outcome events here.                                            |
| exchange    | `payment.event.failure.exchange`   | Receives messages rejected from business queues after Spring Retry is exhausted.           |
| queue       | `payment.succeeded.order.queue`    | Durable queue consumed by the order context to advance the order to paid.                  |
| queue       | `payment.duplicate.refund.queue`   | Durable queue consumed by `ExceptionPaymentRefundConsumer` for duplicate-payment reversal.  |
| queue       | `payment.late.refund.queue`        | Durable queue consumed by `ExceptionPaymentRefundConsumer` for late-payment reversal.       |
| queue       | `payment.succeeded.failure.queue`  | Durable operational failure queue for the succeeded event.                                 |
| queue       | `payment.duplicate.failure.queue`  | Durable operational failure queue for the duplicate event.                                 |
| queue       | `payment.late.failure.queue`       | Durable operational failure queue for the late event.                                      |
| queue       | `payment.event.parking.queue`      | Durable long-term poison-message quarantine; no automatic replay.                           |
| routing key | `payment.succeeded`                | Event exchange to the succeeded order queue.                                                |
| routing key | `payment.duplicate`                | Event exchange to the duplicate refund queue.                                               |
| routing key | `payment.late`                     | Event exchange to the late refund queue.                                                    |
| routing key | `<key>.failure`                    | Business queue DLX to the matching failure queue.                                           |

Business queues are declared with `x-dead-letter-exchange=payment.event.failure.exchange` and
`x-dead-letter-routing-key=<routingKey>.failure`; the failure exchange also binds `payment.event.parking.queue`.
These queues are new, so first deployment only needs the application to declare them (no existing-queue deletion).

## Pre-Deployment Checks

- [ ] Start the backend in a non-production environment and confirm Spring AMQP declares the topology without permission
  errors.
- [ ] Verify all exchanges are durable direct exchanges.
- [ ] Verify delay queues contain `x-dead-letter-exchange`, `x-dead-letter-routing-key`, and `x-message-ttl`.
- [ ] Verify release queues contain `x-dead-letter-exchange` and `x-dead-letter-routing-key` pointing to their dedicated failure exchange/routing key.
- [ ] Verify release queues have active consumers after the backend starts; failure and parking-lot queues intentionally have no application consumers.
- [ ] Confirm no stale queue with incompatible arguments already exists. RabbitMQ rejects redeclaration when queue arguments differ.

### One-time migration for existing environments

This change adds dead-letter arguments to two queues that may already exist. RabbitMQ queue arguments are immutable, so
an environment created before 2026-09-10 must delete only these two release queues before the first deployment of this version:

```text
payment.order.close.release.queue
payment.refund.status-sync.release.queue
```

Stop the backend consumers first, ensure no business message that still needs processing is left in either queue (or export/replay it),
delete the two release queues, then start the backend and let Spring AMQP redeclare them with the new Failure DLX arguments.
Do not delete the delay queues unless their TTL arguments are also being changed.

## Smoke Test

1. Approve a refund in a non-production environment.
2. Confirm a message reaches `payment.refund.status-sync.delay.queue`.
3. Wait for `payment.refund.status-sync-delay-ms`.
4. Confirm the message moves to `payment.refund.status-sync.release.queue` and is consumed.
5. For a controlled failure test, make the downstream refund query fail and confirm the listener retries four attempts, then the broker moves the message to `payment.refund.status-sync.failure.queue` with `x-death` metadata and does not requeue it to the release queue.
6. Confirm the corresponding DB backstop job can still reconcile the business state after the downstream dependency recovers.
7. Confirm application logs contain:

```text
Refund status sync message sent
Start refund status sync message
Finished refund status sync message
```

8. For payment events: trigger a sandbox payment success and confirm a `payment.succeeded` message reaches
   `payment.succeeded.order.queue`, the order becomes paid, and `t_payment_deal` has exactly one row for the order.
9. Trigger a second successful payment on the same order and confirm a `payment.duplicate` message reaches
   `payment.duplicate.refund.queue`, the reversal refund is created for that `payment_no`, and stock is unchanged.
10. Confirm a forced consumer failure retries four times, then lands in the matching `payment.*.failure.queue` with
    `x-death` metadata instead of requeueing to the business queue.

## Rollback Notes

- Rolling back the application stops producing and consuming refund status sync messages.
- Existing delayed/failure/parking-lot messages remain in RabbitMQ until consumed, replayed, purged, or deleted by operations.
- A rollback to a version that declares the old release queue arguments will hit `PRECONDITION_FAILED` while the new release queues still exist. For such a rollback, drain/preserve required messages, stop consumers, delete the two release queues, and let the old version redeclare them.
- Order-close and refund-status-sync failure topologies are independent; do not purge one while operating on the other.
- Payment event queues are additive. Rolling back to a version without `PaymentEventRabbitConfig` stops producing payment
  events; `t_payment_deal` rows written by the newer version are historical facts and must not be deleted as part of a
  code rollback. Drain or preserve unprocessed `payment.*` messages before deleting their queues.
