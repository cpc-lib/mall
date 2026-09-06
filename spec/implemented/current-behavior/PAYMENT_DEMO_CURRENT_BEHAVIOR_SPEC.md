# Payment Demo V5 Current Behavior Spec

## 0. Metadata

- Status: implemented
- Domain: current-behavior
- Updated: 2026-06-09
- Owner: TBD
- Related work: initial characterization tests and spec ledger setup
- Impact scope: backend, React frontend, Vue frontend, database, Redis, RabbitMQ, external payment providers, docs, tests

## 1. Background

This spec records the current externally observable behavior of Payment Demo V5 before further refactoring. It does not judge whether the behavior is correct and does not propose a redesign. It exists to keep public API, state transitions, callback responses, event output, logs, and compatibility behavior stable during small safe changes.

The system includes a Spring Boot backend, React/Vue frontends, MySQL, Redis, RabbitMQ, WeChat Pay V2/V3, and Alipay. The main business surfaces are product listing, order creation/payment, payment callbacks, refund application/review/sync, payment configuration, and delayed order close messages.

## 2. Contract

### 2.1 Public API Surface

| Entry | Type | Current Callers | Current Contract |
|---|---|---|---|
| `GET /api/product/test` | REST | manual/frontend debug | Returns `R` with `code=0`, default success message, `data.message=hello`, and a current `Date` value under `data.now`. |
| `GET /api/product/list` | REST | React/Vue product page | Returns product list under `data.productList`. |
| `GET /api/order-info/list` | REST | React/Vue order page | Returns order list under `data.list`. |
| `GET /api/order-info/query-order-status/{orderNo}` | REST | frontend polling | Success status returns `code=0` and `message=支付成功`; non-success, missing, or closed status currently returns `code=101` and `message=支付中......`. |
| `POST /api/wx-pay/native/{productId}` | REST | frontend | WeChat V3 native payment creation, optionally selecting `paymentAppId`. |
| `POST /api/wx-pay/native/notify` | provider callback | WeChat Pay | WeChat V3 callback with JSON response semantics. |
| `POST /api/wx-pay-v2/native/{productId}` | REST | frontend | WeChat V2 native payment creation, optionally selecting `paymentAppId`. |
| `POST /api/wx-pay-v2/native/notify` | provider callback | WeChat Pay | WeChat V2 callback with XML response semantics. Malformed XML currently returns a failure XML response and does not touch Redis, locks, DB, or business services. |
| `POST /api/ali-pay/trade/page/pay/{productId}` | REST | frontend | Alipay page pay returns an HTML form string. |
| `POST /api/ali-pay/trade/notify` | provider callback | Alipay | Successful processing returns `success`; failure returns `failure`. |
| `POST /api/refund-info/apply` | REST | frontend | New refund request accepts JSON body and returns the pending-review success message. |
| `POST /api/wx-pay/refunds` | REST | frontend/legacy callers | Compatibility route for JSON-body refund request. |
| `POST /api/ali-pay/trade/refund` | REST | frontend/legacy callers | Compatibility route for JSON-body refund request. |
| `POST /api/refund-info/apply/{orderNo}/{reason}` | REST | legacy callers | Legacy refund request passes `refundAmount=null` to the service and returns the same pending-review success message. |
| `GET /api/payment-config/apps` | REST | config UI/manual | Currently returns runtime `PaymentAppConfig` objects as response data. |
| `POST /api/payment-config/reload` | REST | config UI/manual | Reloads payment configuration cache from DB-backed config. |
| `payment.order.close.release.queue` | RabbitMQ | delayed close consumer | Consumes delayed close messages containing `orderNo` and `paymentType`. |

### 2.2 Response Shape

Normal REST responses use `R<T>`:

```json
{
  "code": 0,
  "message": "成功",
  "data": {}
}
```

Current defaults:

- `R.ok()`: `code=0`, `message=成功`, `data` is a map.
- `R.error()`: `code=-1`, `message=失败`, `data` is a map.
- Polling-in-progress status: `code=101`, `message=支付中......`.

### 2.3 State And Side Effects

| State Type | Location | Current Readers | Current Writers | Notes |
|---|---|---|---|---|
| DB | `t_order_info` | order/payment/refund/close flows | order creation, status update, code URL save | Order status is persisted as current enum display text. |
| DB | `t_payment_info` | payment history/query flows | payment notification/query sync | Duplicate insert conflicts are swallowed and logged as idempotent current behavior. |
| DB | `t_refund_info` | refund list/review/sync flows | refund application, review, provider sync | Refund review status and refund channel status are separate concepts. |
| DB | `t_payment_channel`, `t_payment_app` | payment config loader | config CRUD | Dynamic payment config source. |
| Redis | `payment:wx:notify:processed:*`, `payment:wx:v2:notify:processed:*` | payment notification handling | payment notification handling | Notification idempotency keys. |
| Redis/Redisson | lock keys | critical payment/refund/order flows | critical payment/refund/order flows | Distributed lock guard around selected flows. |
| RabbitMQ | `payment.order.close.*` | close consumer | order creation flow | Delayed order close event path. |
| Config | `application.yml`, `wxpay.properties`, `alipay-sandbox.properties` | service/config beans | manual config | Static fallback config still coexists with DB config. |
| Log | controller/service loggers | operators/tests | callback and service flows | Some logs are part of characterization coverage, such as duplicate payment flow logging. |

### 2.4 Current Suspicious Behavior

These items are locked as current behavior before refactoring:

- 现状: `query-order-status` returns polling-in-progress for `NOTPAY`, missing/null status, closed status, and other non-success statuses.
- 现状: `GET /api/payment-config/apps` returns runtime `PaymentAppConfig` objects.
- 现状: malformed WeChat V2 XML notification returns failure XML without touching Redis, distributed lock, DB, or services.
- 现状: duplicate payment flow insert swallows `DuplicateKeyException` and logs that the payment flow already exists.
- 现状: refund summary priority is full success, partial success, processing, abnormal, then restore success.
- 现状: legacy refund path passes `refundAmount=null`.

## 3. Acceptance Criteria

- [x] Public API characterization covers response wrapper shape and selected data keys.
- [x] Suspicious current behavior is marked as `现状` in test names or comments.
- [x] Tests do not connect to real MySQL, Redis, RabbitMQ, WeChat Pay, or Alipay.
- [x] Tests use mocks or standalone objects for isolation.
- [x] Error/rejection paths include malformed WeChat V2 XML and delayed close message blank arguments.
- [x] Event/log behavior includes delayed close RabbitMQ output and duplicate payment flow logging.
- [x] No business implementation is changed by this spec.

## 4. Implementation Anchors

| Area | Anchor |
|---|---|
| Product API | `payment-demo-v5/src/main/java/cc/ivera/controller/ProductController.java` |
| Order polling API | `payment-demo-v5/src/main/java/cc/ivera/controller/OrderInfoController.java` |
| Refund application API | `payment-demo-v5/src/main/java/cc/ivera/controller/RefundApplicationController.java` |
| Payment config API | `payment-demo-v5/src/main/java/cc/ivera/controller/PaymentConfigController.java` |
| WeChat V2 notification | `payment-demo-v5/src/main/java/cc/ivera/controller/WxPayV2Controller.java` |
| Payment flow logging/idempotency | `payment-demo-v5/src/main/java/cc/ivera/service/impl/PaymentInfoServiceImpl.java` |
| Delayed close message | `payment-demo-v5/src/main/java/cc/ivera/service/impl/OrderCloseMessageServiceImpl.java` |
| Refund summary status | `payment-demo-v5/src/main/java/cc/ivera/service/refund/OrderRefundStatusService.java` |
| Public API characterization tests | `payment-demo-v5/src/test/java/cc/ivera/characterization/PublicApiCharacterizationTest.java` |
| Infrastructure characterization tests | `payment-demo-v5/src/test/java/cc/ivera/characterization/InfrastructureBehaviorCharacterizationTest.java` |

## 5. Compatibility Impact

This spec introduces no runtime behavior change. It records the compatibility surface that future changes must preserve or explicitly revise through a planned spec.

Future changes require compatibility review if they alter:

- HTTP path, method, request parameters, validation, or response body shape.
- `R<T>` default code/message/data structure.
- Response text such as `支付成功`, `支付中......`, refund pending-review message, `success`, `failure`, or WeChat XML response.
- Order/refund status values or priority rules.
- Redis key prefixes, RabbitMQ exchange/routing key/queue names, or delayed close message fields.
- Payment configuration source selection or fields exposed from config endpoints.
- Legacy refund routes or old frontend API wrappers.

## 6. Verification

Current characterization test command:

```powershell
mvn "-Dtest=PublicApiCharacterizationTest,InfrastructureBehaviorCharacterizationTest" test
```

Last known result on 2026-06-09: 18 tests passed, 0 failures, 0 errors.

## 7. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-06-09 | implemented | Created current behavior spec and linked characterization tests. | Initial spec ledger setup |

