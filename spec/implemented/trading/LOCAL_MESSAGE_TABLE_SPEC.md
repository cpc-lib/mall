# LOCAL_MESSAGE_TABLE_SPEC — 本地消息表（事务性发件箱）与消费成功回写

- 状态：`implemented`（2026-09-06：mvn compile 通过；发件箱实体/Mapper/服务/定时任务、消费者回写 CONSUMED、payment_demo.sql 与 upgrade_local_message.sql 均落盘；运行期 PENDING→SENT→CONSUMED 流转验证待部署后执行）
- 日期：2026-09-06
- 问题分类：Design change（消息可靠性架构 / 状态规则 / DB schema 变更）
- 需求来源：用户要求"消费者消费成功处理之后，才能确认消息队列消息已被确认消费，并修改本地消息表的状态"；此前追问幂等性、MQ 重试指数回避、超限人工补偿、MQ 挂掉的定时兜底。

## 1. 问题与目标

### 1.1 现状（改造前）

| 环节 | 动作 | 问题 |
|---|---|---|
| 延迟关单消息发送 | `afterCommit` 直发 MQ（CheckoutServiceImpl / OrderInfoServiceImpl） | 业务提交与消息发送非原子：提交后、发送前应用崩溃 → 消息永久丢失 |
| 退款同步消息发送 | `approve` 内直发 MQ（RefundApplicationServiceImpl） | 同上，且发送失败会中断审核主流程 |
| 发送者确认 | publisher-confirm 仅打日志 | confirm 失败只留痕，无自动重投 |
| 消费成功 | Spring `acknowledge-mode=auto`（监听器成功才 ack） | MQ 层 ack 正确，但无业务侧"已消费"留痕，无法观测消息链路终态 |

### 1.2 目标（本地消息表 = 事务性发件箱）

```
状态流转：PENDING（业务事务内落库，与业务变更原子）
        → SENT（事务提交后投递 MQ，且发送者确认到达才置位）
        → CONSUMED（消费者监听器成功返回后回写；此时 Spring 才向 broker ack）
异常分支：投递失败/确认超时 → 指数回避重试（3^n 秒：3s→9s→27s→81s→243s）
        → 超过 5 次置 FAILED（保留在表内，人工补偿）
兜底：LocalMessageSendJob 每 30s 扫描 PENDING 且到达 next_retry_time 的消息重投
```

关键语义（对应需求）：

1. **业务事务原子**：消息落库与订单/退款变更同事务提交，不存在"业务成功但消息丢失"窗口。
2. **SENT 严格口径**：`rabbitTemplate.convertAndSend` 后同步等待 publisher-confirm（correlated，5s 超时），**broker 确认到达才置 SENT**；nack/超时按失败排期重试。
3. **消费成功才回写 CONSUMED**：消费者监听器方法正常返回（此时 Spring AMQP 才向 broker 发送 basic.ack）之后，按 `(biz_type, biz_no)` 回写 CONSUMED；监听器抛异常则不回写，消息按 MQ 重试策略处理（2s→6s→18s×4 后 reject 不回队，由 DB 兜底定时任务对账收敛）。
4. **MQ 挂掉**：发送抛异常 → 消息留在 PENDING，发件箱定时任务指数回避重投；恢复后自动补投。
5. **幂等**：
   - 消费端业务幂等（关单状态 CAS、退款同步状态 CAS）不变，重复投递无副作用；
   - 状态回写均为条件更新（CAS）：SENT 仅可从 PENDING 流转、CONSUMED 仅可从 PENDING/SENT 流转，发件箱重试与快速消费竞态不会互相覆盖；
   - 同一 `(biz_type, biz_no)` 的消息按业务动作仅落库一次（关单：下单时一次；退款同步：审核通过时一次）。

## 2. 数据库变更

### 2.1 全量脚本 payment_demo.sql（新装库）

新增 `t_local_message`：

| 列 | 类型 | 说明 |
|---|---|---|
| id | BIGINT IDENTITY PK | |
| biz_type | VARCHAR(32) NOT NULL | ORDER_CLOSE / REFUND_SYNC |
| biz_no | VARCHAR(64) NOT NULL | orderNo / refundNo |
| message_content | CLOB NOT NULL | 消息体 JSON（与 MQ 消息体一致） |
| status | VARCHAR(16) DEFAULT 'PENDING' NOT NULL | PENDING / SENT / CONSUMED / FAILED |
| retry_count | INT DEFAULT 0 NOT NULL | 投递重试次数 |
| next_retry_time | TIMESTAMP NOT NULL | 下次重试时间 |
| create_time / update_time | TIMESTAMP | |

索引：`idx_local_message_scan(status, next_retry_time)`（发件箱扫描）、`idx_local_message_biz(biz_type, biz_no)`（消费回写定位）。

### 2.2 增量脚本 upgrade_local_message.sql（存量库）

新建 t_local_message + 索引 + 注释；重复执行在建表处报"对象已存在"，属预期。存量库执行后重启后端生效。

## 3. API 契约与兼容性影响

| 契约 | 变更 | 兼容性 |
|---|---|---|
| MQ 消息体（OrderCloseMessage / RefundStatusSyncMessage） | 不变（Java 序列化 POJO，SimpleMessageConverter） | 完全兼容；发件箱重投前从落库 JSON 还原为原 POJO 再发送，保证线上格式不变 |
| 队列/交换机拓扑 | 不变 | 完全兼容 |
| 业务接口（下单/退款审核） | 不变 | 完全兼容 |
| 内部服务接口 | `OrderCloseMessageService` / `RefundStatusSyncMessageService` 签名不变，实现改为发件箱；新增 `LocalMessageService`、`LocalMessageMapper`、`LocalMessageSendJob` | 仅内部 |
| 公共 HTTP API | 无变更 | 无 |

## 4. 实现锚点

| 锚点 | 位置 |
|---|---|
| 实体与状态常量 | `cc/ivera/entity/LocalMessage.java` |
| Mapper + 扫描 SQL | `cc/ivera/mapper/LocalMessageMapper.java`、`resources/mapper/LocalMessageMapper.xml` |
| 发件箱服务（落库/投递/确认等待/重试排期/消费回写） | `cc/ivera/service/LocalMessageService.java`、`cc/ivera/service/impl/LocalMessageServiceImpl.java` |
| 兜底定时任务（30s 扫描） | `cc/ivera/job/LocalMessageSendJob.java` |
| 延迟关单消息改发件箱 | `cc/ivera/service/impl/OrderCloseMessageServiceImpl.java` |
| 退款同步消息改发件箱 | `cc/ivera/service/impl/RefundStatusSyncMessageServiceImpl.java` |
| 下单调用点：消息落库移入事务内 | `cc/ivera/service/impl/CheckoutServiceImpl.java`（createOrder）、`cc/ivera/service/impl/OrderInfoServiceImpl.java`（createOrReuseOrder，删除 sendCloseOrderMessageAfterCommit 直发 helper） |
| 消费成功回写 CONSUMED | `cc/ivera/mq/OrderCloseConsumer.java`、`cc/ivera/mq/RefundStatusSyncConsumer.java`（监听器主体成功后调用 markConsumed） |
| 关单收口同事务回写（兜底对账/强制关单也终结消息） | `cc/ivera/service/impl/OrderInfoServiceImpl.java`（updateStatusByOrderNoIfStatus：NOTPAY→CLOSED/CANCEL 分支内 markConsumed ORDER_CLOSE，与关支付单/释放库存同事务） |
| JSON 工具新增 toObject(json, type) | `cc/ivera/util/JsonUtils.java` |

## 5. 运维与补偿

- FAILED 消息保留在 t_local_message，人工补偿方式：修复故障后 `UPDATE t_local_message SET status='PENDING', next_retry_time=CURRENT_TIMESTAMP WHERE status='FAILED'`，发件箱任务自动重投（消费端幂等保证安全）。
- ~~SENT 长期未 CONSUMED（旧设计：兜底任务收敛业务但消息仅作观测）~~ **已修正（2026-09-06）**：延迟关单消息的终态不再只依赖 MQ 消费者 ack。无论由 `OrderCloseConsumer`（MQ 延迟）、`TimeoutOrderCloseScheduler`（DB 兜底对账）还是管理端强制关单触发，只要订单在 `OrderInfoServiceImpl.updateStatusByOrderNoIfStatus` 收口点 CAS 离开 NOTPAY → CLOSED/CANCEL，就同事务回写该订单 ORDER_CLOSE 消息为 CONSUMED，与「关闭活跃支付单 + 释放预占库存」原子提交。修复「定时关单已释放库存、t_local_message 仍悬挂 PENDING/SENT」的观测不一致。
- 退款同步消息（REFUND_SYNC）仍由消费者回写为主；兜底对账不直接回写其消息状态（退款状态机与关单不同，本期不改）。
- 后续增强（不在本期）：管理端发件箱监控页面（状态/重试次数展示 + 手动重试按钮）、过期 CONSUMED 数据清理任务。

## 6. 验收标准

- [x] 新装库执行 payment_demo.sql 包含 t_local_message 建表；存量库提供 upgrade_local_message.sql（验收证据：两脚本落盘，DDL 与本 spec 第 2 节一致）。
- [x] 延迟关单消息：下单事务内落库 PENDING，提交后投递，broker 确认后置 SENT（验收锚点：CheckoutServiceImpl createOrder / OrderInfoServiceImpl createOrReuseOrder / LocalMessageServiceImpl.publishOne）。
- [x] 退款同步消息：审核通过后经发件箱投递（验收锚点：RefundStatusSyncMessageServiceImpl）。
- [x] 消费者监听器成功返回后回写 CONSUMED，异常时不回写（验收锚点：OrderCloseConsumer / RefundStatusSyncConsumer，markConsumed 位于 process 成功之后）。
- [x] 投递失败指数回避重试，超限置 FAILED（验收锚点：LocalMessageServiceImpl.scheduleRetry，3^n 秒、MAX_RETRY_COUNT=5）。
- [x] 兜底定时任务 30s 扫描 PENDING 重投（验收锚点：LocalMessageSendJob）。
- [x] 状态回写为条件更新，避免竞态覆盖（验收锚点：LocalMessageServiceImpl.markSent/markConsumed/scheduleRetry 均带 status 条件）。
- [x] `mvn compile` 通过（2026-09-06，BUILD SUCCESS）。
- [ ] 运行期验证：下单 → 观察 t_local_message PENDING→SENT→CONSUMED 流转；停 MQ 重启后消息自动补投（部署后人工执行，需 DM8 + RabbitMQ 环境）。
- [x] 关单收口同事务回写：订单 NOTPAY→CLOSED/CANCEL 时（MQ 消费者 / TimeoutOrderCloseScheduler 兜底 / 管理端强制关单 / 用户取消均经此收口）同事务 markConsumed(ORDER_CLOSE)，库存释放与消息终结原子一致（验收锚点：OrderInfoServiceImpl.updateStatusByOrderNoIfStatus；mvn compile 通过 2026-09-06）。

## 7. Change Log

| 日期 | 变更 |
|---|---|
| 2026-09-06 | 初版：事务性发件箱落地，消费成功回写 CONSUMED，指数回避重试 + FAILED 人工补偿 + 30s 兜底扫描。 |
| 2026-09-06 | 修复「定时关单已释放库存但 t_local_message 不回写」：ORDER_CLOSE 消息终态改由关单收口点同事务驱动（OrderInfoServiceImpl.updateStatusByOrderNoIfStatus NOTPAY→CLOSED/CANCEL 内 markConsumed），覆盖 MQ 消费者 / TimeoutOrderCloseScheduler 兜底 / 管理端强制关单 / 用户取消；mvn compile 通过。 |
