# ORDER_TIMEOUT_CONFIG_SPEC — 本地订单未支付超时统一可配置（默认 3 分钟）并释放锁定库存

- 状态：`planned`
- 日期：2026-09-06
- 问题分类：Design change（配置来源统一 + 状态规则参数变更）
- 需求来源：用户要求"本地订单超过一定时间（如 3 分钟）没有发起支付，应释放锁定的商品库存并关闭本地订单"。

## 1. 现状与结论

超时关单 + 库存释放机制**已存在**（双路径，均走"渠道查单 → 必要时关渠道 → 本地 CAS NOTPAY→CLOSED + 关闭支付单 + releaseReservation 释放锁定库存 + ORDER_RELEASE 流水"）：

1. **MQ 延迟关单**：下单事务提交后发延迟消息，延迟队列 `x-message-ttl` 取 `payment.order.close-delay-ms`（默认 900000ms=15 分钟）。
2. **定时兜底**：`TimeoutOrderCloseScheduler` 每 `payment.order.timeout-scan-ms`（默认 60s）扫描 create_time 早于**硬编码 15 分钟**的 NOTPAY 订单。

缺陷：超时时长分散在 4 处独立定义（两处 `ORDER_EXPIRE_MINUTES=15L` 常量、MQ TTL 默认值、调度器硬编码 15 分钟），易漂移且不可配置；默认 15 分钟与用户预期（3 分钟量级）不符。

## 2. 设计

统一为**单一配置属性** `payment.order.expire-minutes`（默认 3，分钟），全部派生自它：

| 位置 | 原值 | 新值 |
|---|---|---|
| CheckoutServiceImpl（下单 expire_time） | 常量 15L | `${payment.order.expire-minutes:3}` |
| OrderInfoServiceImpl（快速下单 expire_time） | 常量 15L | `${payment.order.expire-minutes:3}` |
| OrderCloseRabbitConfig（延迟队列 x-message-ttl） | `${payment.order.close-delay-ms:900000}` | `expire-minutes * 60000` |
| TimeoutOrderCloseScheduler（扫描截止） | 硬编码 15 分钟 | `${payment.order.expire-minutes:3}` |
| application.yml | `close-delay-ms: 900000` | `expire-minutes: 3` |

- `payment.order.close-delay-ms` 属性废弃删除（唯一来源原则）。
- "发起支付后仍在倒计时内完成支付"不受影响：已支付订单 NOTPAY 状态已变更，关单路径幂等跳过；3 分钟后晚到支付由既有的晚到支付冲正链路自动退款。
- 渠道侧交易若已发起但未完成支付：关单前先查渠道并尝试关渠道（既有逻辑，不变）。
- 扫描间隔 `payment.order.timeout-scan-ms` 保持独立可配置（默认 60s），兜底关单时间点为 3~4 分钟。

## 3. 兼容性影响

- 公共 API 契约不变（订单仍带 expire_time 字段，前端倒计时自动跟随新默认值）。
- **RabbitMQ 运维影响**：`payment.order.close.delay.queue` 的 `x-message-ttl` 属于队列参数，RabbitMQ 不允许对已存在队列变更参数——存量环境需**删除一次该队列**（重启后端时由 RabbitAdmin 以新 TTL 重建），否则延迟关单仍按旧 15 分钟触发（定时兜底仍会按新配置关单，不影响正确性，只影响及时性）。
- 回滚：恢复 yml 属性名与四处代码即可，无 DB 变更。

## 4. 验收标准

1. [x] `mvn compile` 通过（2026-09-06，EXIT=0）。
2. [x] 全仓不再存在 `ORDER_EXPIRE_MINUTES` 常量与 `close-delay-ms` 引用（代码+yml 已 rg 验证；文档同步更新）。
3. [x] 存量 RabbitMQ 延迟队列已通过管理 API（15672）删除，重启后端时由 RabbitAdmin 以新 TTL（180000ms）重建。
4. [x] 文档（CODE_INTRO / RABBITMQ_OPERATIONS / characterization 规范）同步更新。

## 5. Change Log

- 2026-09-06：创建 planned spec。
- 2026-09-06：实现落地（四处超时来源统一为 `payment.order.expire-minutes` 默认 3：CheckoutServiceImpl / OrderInfoServiceImpl 下单 expire_time、OrderCloseRabbitConfig 延迟队列 TTL、TimeoutOrderCloseScheduler 扫描截止；yml 与文档同步；存量延迟队列已删除）；迁移 implemented。遗留：重启后端后按 3 分钟超时实测一轮下单-关单-库存释放（关单链路本身为既有已验证行为，本次仅改时长来源）。
