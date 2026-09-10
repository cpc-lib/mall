# 电商商城平台 Agent Rules

> 适用范围：仓库根目录及其全部子目录。目标是让任何编码 Agent 在修改本项目时保持**小步、可验证、可回滚、与现有领域规则一致**。

## 1. 项目事实基线

本仓库是一个前后端分离的电商交易系统：

- `backend/`：Spring Boot 2.3.7.RELEASE、Java 8、MyBatis-Plus 3.3.1、达梦 DM8、Redis + Redisson、RabbitMQ、MinIO（可选）。
- `user-ui/`：React 18 + Vite 5 + Ant Design 5，用户商城，开发端口 `3000`。
- `admin-ui/`：React 18 + Vite 5 + Ant Design 5 + SheetJS + Luckysheet，管理后台，开发端口 `3002`。
- 后端端口：`8080`；两个前端通过 CORS 直接访问 `http://localhost:8080`，当前无 Vite 代理。
- 后端按 8 个限界上下文组织：`shared`、`product`、`order`、`payment`、`refund`、`user`、`cart`、`bill`。
- 每个业务上下文遵循四层结构：`interfaces / application / domain / infrastructure`。
- 当前测试基线：后端 **23 个测试类、151 个用例**；`user-ui` 有 1 个 Node 逻辑测试；`admin-ui` 暂无自动化测试。

文档职责：

- `README.md`：面向使用者，说明项目能力、技术栈、启动方式、配置与测试。
- `CODE_INTRO.md`：面向开发者，说明架构、领域边界、数据模型、交易链路、并发与 MQ 设计。
- `backend/docs/DAMENG_DM8_OPERATIONS.md`：DM8 运维。
- `backend/docs/RABBITMQ_OPERATIONS.md`：RabbitMQ 拓扑、迁移、失败队列、冒烟与回滚。

## 2. 修改前先定性

任何改动开始前都应归类；不明确时按“设计变更”处理。

| 类型 | 判定 | 必须动作 |
|---|---|---|
| 局部缺陷 | 既有契约明确，仅单点行为错误 | 先补复现测试，再做最小修复，跑受影响测试和全量回归 |
| 设计变更 | 影响工作流、状态机、领域模型、架构边界、配置来源、MQ 拓扑、支付渠道行为 | 明确“现状 → 目标 → 影响面 → 回滚”，同步 `CODE_INTRO.md` |
| 公共 API / 兼容性变更 | 路由、请求响应、状态值、配置键、DB schema、事件名、队列名、日志/审计契约变化 | 必须说明兼容性与迁移步骤；涉及 DB 时只修改统一 `schema.sql` |
| 多问题同根因 | 多个问题指向同一规则、配置、幂等锚或状态推进机制 | 先修共享根因，禁止逐个打补丁 |

## 3. 分支与提交约束

仅使用以下分支前缀：

- `bug-fix/<topic>`：局部缺陷。
- `feature/<topic>`：新能力或设计变更。
- `refactor/<topic>`：行为保持不变的重构。
- `update/<topic>`：文档、配置、治理类修改。

提交、分支、PR 标题中不要加入 Agent、模型、厂商、生成器名称或自动生成署名。

## 4. DDD 与依赖方向

后端上下文内部统一：

```text
cc.ivera.<context>/
├── interfaces       REST Controller / DTO / VO / MQ Consumer / Scheduler
├── application      用例编排、事务边界、应用服务
├── domain           领域模型、枚举、策略、repository/gateway 端口
└── infrastructure   PO / Mapper / Converter / RepositoryImpl / 外部适配 / MQ 拓扑
```

必须遵守：

- `domain` 不得依赖 Spring、MyBatis-Plus 或具体 infrastructure 实现。
- 跨上下文只允许依赖对方 `domain` 中的模型、repository/gateway 端口，或明确的 application 服务接口。
- 禁止跨上下文直接引用对方 `infrastructure` 下的 PO、Mapper、RepositoryImpl。
- PO 只属于 `infrastructure.persistence.po`；领域模型与数据库模型保持分离。
- 防并发的关键条件 SQL（CAS / 条件 UPDATE）保留在 Mapper/XML，不要用“先查后改”替换。

## 5. 不得破坏的领域不变量

### 5.1 库存四桶

```text
available_stock + locked_stock + sold_stock + lost_stock = 入库总量
```

任何库存变化必须：

1. 使用条件 UPDATE 保证桶数量不为负；
2. 写 `t_inventory_transaction`；
3. 使用 `biz_no`（典型格式 `TYPE:单号:itemId`）作为幂等锚；
4. 依赖数据库唯一键作为最终兜底。

防超卖根闸门是 `ProductMapper.reserveStock`：

```sql
WHERE available_stock >= qty
```

库存不足必须让整个下单事务失败，不允许先生成有效订单后再补偿扣库存。

### 5.2 库存状态语义

- 下单：`available → locked`，预占状态 `LOCKED`。
- 支付成功：预占 `LOCKED → COMMITTED`，**库存数量不变，仍在 locked 桶**。
- 超时关单/取消：`locked → available`，预占 `LOCKED → RELEASED`。
- 确认收货：`locked → sold`。
- 已发货未收货 `REFUND_ONLY`：管理员受理前必须确认商品去向；`LOST` 执行 `locked → lost`，`RECOVERED` 执行 `locked → available`，不得在未判定时直接发起渠道退款。
- 已收货 `REFUND_ONLY`：`sold → lost`。
- `RETURN_AND_REFUND`：必须在退货签收/质检后才可 `sold → available`。
- 差价退款、重复支付、晚到支付冲正：不回补库存。

### 5.3 支付成交唯一性

- 一个业务订单允许 `1:N` 本地支付尝试（`t_payment_order`）。
- 同渠道活跃支付单优先复用；跨渠道可以有不同支付尝试。
- 一个业务订单只允许一笔真正有效的 `SUCCESS` 成交。
- 成交后关闭其它活跃支付尝试。
- 重复支付、关单后晚到支付由 `PaymentSuccessService` / `ExceptionRefundService` 走原路退款冲正，**冲正不改库存**。

### 5.4 退款防超退

- 金额与数量校验以服务端 `refund.domain.policy.RefundPolicy` 为准。
- 退款受理时冻结可退额度，禁止先退款后补校验。
- 用户端前置计算仅用于体验，不得取代后端最终核算。
- 最后一件商品允许按服务端规则处理金额尾差。

### 5.5 账账核对不变量

- 渠道交易账以 `t_bill_record` 为渠道账本；平台支付账以 `t_payment_order`、平台退款账以 `t_refund_order` 为权威账本。
- 支付核对必须保留订单 `1:N PaymentOrder` 逐笔语义，优先按 `channel_order_no` 对渠道微信订单号，禁止按 `order_no` 聚合后核对。
- 平台支付日切必须使用 `PaymentOrder.paid_time`，退款日切必须使用 `RefundOrder.success_time`，统一按 `Asia/Shanghai` 的 `[00:00, 次日00:00)` 归账。
- 对账必须双向扫描：既检查“渠道有平台无”，也检查“平台有渠道无”；金额相等不能替代逐笔匹配。
- `balanced=true` 必须同时满足支付笔数/金额一致、退款笔数/金额一致、净额一致且不存在 `OPEN` 差异，禁止仅凭净额为 0 判平账。
- 交易账净额不包含渠道手续费；手续费只能进入独立的资金/结算账核对口径，禁止混账。
- 差异审计必须同时保留渠道侧标识和平台侧 `local_biz_no/local_ledger_no/local_serial_no`，便于人工追溯。
- 日切查询必须保留 `idx_payment_order_channel_status_paid_time`、`idx_refund_order_status_success_time` 等索引，禁止无评估删除。

### 5.6 状态推进与并发

订单、支付、退款、库存等关键推进采用：

```text
Redis / Redisson 分布式锁
        +
CAS 条件更新
        +
数据库唯一约束 / biz_no 幂等
```

禁止将关键状态机改造成无条件 UPDATE。

**禁止引入 `select ... for update` 作为本项目的并发主方案。** 当前项目的并发权威是分布式锁 + CAS；远程支付渠道调用期间不得长时间持有数据库行锁。

## 6. RabbitMQ 可靠性约束

当前消费者模式固定为：

```yaml
spring.rabbitmq.listener.simple.acknowledge-mode: manual
spring.rabbitmq.listener.simple.default-requeue-rejected: false
```

当前契约是 **MANUAL ACK + Spring Retry + Failure DLQ**：

```text
Listener 成功完成业务
→ markConsumed
→ Channel.basicAck(deliveryTag, false)

Listener / markConsumed / basicAck 抛异常
→ 异常继续向外抛
→ Spring Retry（最多 4 次）
→ 重试耗尽 RejectAndDontRequeue
→ Release Queue DLX
→ Failure Queue
```

实现约束：

- Consumer 成功路径必须显式 `basicAck(deliveryTag, false)`，禁止提前 ACK。
- 业务异常与 `markConsumed` 异常不得 ACK，也不要在第一次失败时手工 `basicNack`，否则会绕过现有 Spring Retry。
- 空/无效但明确选择忽略的消息必须显式 ACK，避免 MANUAL 模式下形成长期 unacked。
- 不得 catch 后吞异常；失败必须继续交给容器重试，重试耗尽后由现有 Failure DLQ 接管。

订单关单与退款同步各自拥有独立失败拓扑：

```text
Delay Queue
  → Release Queue
      → Consumer
          → Failure Exchange
              → Failure Queue
              → Parking Lot Queue（人工隔离，不自动回灌）
```

必须保持以下职责边界：

- `Failure Queue`：保留最终消费失败消息，供排障、审计、人工重放。
- `Parking Lot Queue`：人工长期隔离 poison message，不配置自动 Consumer，不形成自动循环。
- `TimeoutOrderCloseScheduler` / `RefundStatusSyncScheduler`：DB 驱动的最终一致性兜底，与 Failure Queue 并行存在；订单扫描周期由 `payment.order.timeout-scan-ms` 控制，退款扫描周期由 `payment.refund.status-sync-scan-ms` 独立控制。单笔失败只能记录日志并等待后续收敛，不得中断同批次其它记录。
- `t_local_message`：Transactional Outbox，负责“业务事务落库后可靠投递”，不是消费失败队列的替代品。

修改已有 RabbitMQ Queue arguments（TTL、DLX 等）时必须注意 RabbitMQ 参数不可原地修改；需要在运维文档中给出删旧队列再重声明的迁移步骤。

## 7. 数据库变更规则

数据库唯一权威脚本：

```text
backend/env/sql/dm8/schema.sql
```

要求：

- 不再维护新的增量 SQL 作为最终交付。
- schema 变更只修改 `schema.sql`。
- 脚本保持可重复执行，包含必要的旧对象清理/守卫。
- 全新库与存量测试库执行后的目标结构必须一致。
- 表、索引、字段、约束命名必须考虑 DM8 语法与关键字差异。

## 8. 测试规则

后端测试位于 `backend/src/test/java`，当前基线：**23 个测试类、151 个用例**。

测试原则：

- 新行为：必须补测试。
- 缺陷修复：先写能复现问题的测试。
- 重构：先用特征测试锁住原行为。
- 测试不连接真实 DM8、Redis、RabbitMQ、微信或支付宝；使用 JUnit 5 + Mockito / 纯对象测试。
- 涉及 MQ 消费者时必须验证：成功路径严格按“业务 → CONSUMED → `basicAck`”顺序执行；业务/CONSUMED 回写失败时不 ACK；明确忽略的无效消息会 ACK；`basicAck` 自身失败必须继续向外抛出。
- 涉及 MQ 拓扑时必须验证 release queue 的 `x-dead-letter-exchange` / `x-dead-letter-routing-key` 以及 Failure/Parking Lot binding。
- 涉及 DB 最终一致性兜底时必须验证 Scheduler 会扫描目标状态、复用既有业务链路，并且单条失败不会阻塞同批次后续记录。
- 涉及账账核对时必须验证：订单 1:N 支付不会被折叠、渠道流水错配会产出差异、渠道/平台双向单边账可识别、汇总平衡条件不会被金额抵消绕过。

必须执行的回归：

```powershell
cd backend
mvn test

cd ../user-ui
npm run test:logic
```

触碰前端页面时还应执行对应构建：

```powershell
npm run build
```

`admin-ui` 当前没有逻辑测试，改动时至少执行 `npm run build`。

## 9. 文档同步规则

下列变化必须同步 `CODE_INTRO.md`：

- 限界上下文、依赖方向、聚合或核心实体；
- 状态机、库存、支付、退款、幂等规则；
- MQ 拓扑、Outbox、定时兜底；
- Controller 路由、前端页面能力；
- 测试基线。

下列变化必须同步 `README.md`：

- 新功能与删除功能；
- 启动方式、依赖、端口；
- 配置项；
- 测试命令；
- 运维迁移注意事项。

实现、测试、文档不一致，视为未完成。

## 10. 修改风格

- 只改与任务直接相关的代码，不顺手大范围格式化或重构。
- 优先复用已有领域服务和端口，避免建立第二套相同规则。
- 不新增“看起来以后可能有用”的抽象。
- 不吞异常来制造“成功”；异常是否重试必须与上层调用契约一致。
- 不硬编码新的密钥、Token、商户私钥或生产凭据；生产敏感值必须通过环境变量、数据库安全配置或部署系统提供。
- 发现无关问题可以记录，但不要夹带修复。

## 11. Definition of Done

一项改动完成前至少确认：

- [ ] 问题类型和目标行为明确。
- [ ] 领域不变量未被破坏。
- [ ] 跨上下文依赖方向正确。
- [ ] API / DB / MQ 兼容性影响已记录。
- [ ] 新增或受影响测试已通过。
- [ ] `mvn test` 全量通过。
- [ ] 需要时 `user-ui` / `admin-ui` 构建通过。
- [ ] `README.md` / `CODE_INTRO.md` / 运维文档与代码一致。
- [ ] 没有无关代码、无关格式化或隐式行为变化。
