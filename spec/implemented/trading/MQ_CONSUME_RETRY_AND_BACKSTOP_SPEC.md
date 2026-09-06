# MQ_CONSUME_RETRY_AND_BACKSTOP_SPEC — 消息消费重试（指数回避）与 DB 兜底对账幂等性审计与补强

- 状态：`implemented`（2026-09-06：mvn compile 通过；listener 重试/消费者确认/发送者确认与退回配置落盘；RefundStatusSyncScheduler、RabbitReliabilityConfig 落盘）
- 日期：2026-09-06
- 问题分类：Design change（可靠性策略 / 新增兜底任务）
- 需求来源：用户审计四问——①是否使用本地消息表 ②消费重试有无指数回避 ③超次数有无人工补偿 ④MQ 挂掉有无定时任务兜底并保证幂等。

## 1. 审计结论（现状）

| 问题 | 答案 | 说明 |
|---|---|---|
| ① 本地消息表（事务性发件箱） | **未使用** | 关单消息在订单事务提交后（afterCommit）发送。不采用发件箱的原因：订单/退款单状态以 DB 为唯一事实源，两条 DB 驱动的兜底扫描（本 spec 之前已有超时关单调度器 + 本次新增退款同步调度器）本质上等价于"消息补偿任务"，发件箱属冗余。发送侧丢失（提交后未发出即宕机）由兜底扫描 ≤60s 内补齐 |
| ② 消费重试指数回避 | **此前无**（默认立即无限回队热重试） | 本次补强：Spring AMQP listener retry：2s→6s→18s 共 4 次 |
| ③ 超次数人工补偿 | **无死信队列，靠 DB 兜底 + 管理端人工操作** | 订单：`POST /api/admin/order/{orderNo}/force-close`（双端管理页均有"强制关单"按钮）；退款：`GET 管理端退款状态查询`（触发渠道同步）。重试耗尽的消息被拒绝丢弃，但业务状态由兜底任务继续收敛，不丢业务 |
| ④ MQ 挂掉定时兜底 + 幂等 | 关单**已有**；退款同步**本次新增** | 兜底任务不依赖 MQ，直接扫 DB；关单/退款同步全链路幂等（见 §3） |

## 2. 本次变更

1. **消费重试策略**（application.yml `spring.rabbitmq.listener.simple`）：
   - `retry.enabled=true, max-attempts=4, initial-interval=2000, multiplier=3.0, max-interval=20000`（2s→6s→18s）
   - `default-requeue-rejected=false`：重试耗尽后拒绝不回队，杜绝无限热重试；丢弃安全的前提是 §1④ 的 DB 兜底
   - 作用于全部 @RabbitListener（OrderCloseConsumer / RefundStatusSyncConsumer），统一策略
2. **新增 RefundStatusSyncScheduler**（job 包）：
   - 每 `payment.refund.status-sync-delay-ms`（默认 60s，与 MQ 延迟 TTL 共用同一配置）扫描 `t_refund_info: approval_status=APPROVED AND refund_status=PROCESSING`
   - 逐单调用既有 `RefundApplicationService.queryRefundStatus`（渠道查单→CAS 回写），DM8 LIMIT 兼容退化为全量扫描（与超时关单调度器同模式）
   - 与 MQ 消费并发安全（同一退款单号分布式锁 + CAS），重复对账无副作用

## 3. 幂等性依据（全链路）

| 链路 | 幂等机制 |
|---|---|
| 关单（MQ 触发 / 调度器触发，同一路径） | 订单 CAS `NOTPAY→CLOSED`；支付单 CAS 关闭；库存释放 reservation CAS + `ORDER_RELEASE:{orderNo}:itemId` bizNo 唯一键；重复触发/并发触发均收敛到同一终态 |
| 退款同步（MQ 触发 / 调度器触发 / 人工查询触发） | 退款单号分布式锁 + `updateRefundIfStatusIn` CAS；订单退款汇总状态按明细重算幂等 |
| 强制关单（人工补偿） | 已关闭直接返回成功（幂等） |
| 渠道侧关单 | 渠道关单接口按渠道单号幂等；本地仅状态 CAS 成功才继续 |

## 4. 时序保障（SLA 口径）

- 正常：关单在 expire_time 精确触发（MQ TTL）；退款同步在发起后约 1 分钟触发
- MQ 不可用/消息丢失/消费重试耗尽：兜底扫描每 60s 一轮，最多再延迟一个扫描周期（60~120s）完成对账
- 消费失败重试总时长 ≈ 26s（2+6+18），之后交由兜底，不阻塞队列

## 5. 兼容性影响

- 不改公共 API；application.yml 新增 listener 配置为 Spring AMQP 标准配置
- 不改 DB schema；RefundStatusSyncScheduler 为新增只读扫描 + 既有幂等写路径
- 回滚：删除 yml listener 块与 RefundStatusSyncScheduler 即可

## 6. 验收标准

1. [x] `mvn compile` 通过（2026-09-06，EXIT=0）。
2. [x] application.yml listener retry + publisher confirm/returns/mandatory 配置生效（标准 Spring Boot 2.3 支持的配置项，rg 复核落盘）。
3. [x] RefundStatusSyncScheduler / RabbitReliabilityConfig 落盘（rg 验证），复用既有幂等服务，无新增写路径。
4. [x] RABBITMQ_OPERATIONS.md 同步更新重试、兜底与投递可靠性说明。

## 7. Change Log

- 2026-09-06：创建 planned spec。
- 2026-09-06：实现落地（listener 指数回避重试 + RefundStatusSyncScheduler 退款同步兜底 + 发送者确认/退回与消费者确认显式化 + CorrelationData 单号透传）；审计结论固化；迁移 implemented。遗留：重启后端后观察一轮（可临时停 RabbitMQ 验证兜底扫描独立工作）。
