# 电商商城平台 Agent Rules

> 适用范围：仓库根目录及其全部子目录。目标是让任何编码 Agent 在修改本项目时保持**小步、可验证、可回滚、与现有领域规则一致**。

## 1. 项目事实基线

本仓库是一个前后端分离的电商交易系统：

- `backend/`：Maven 聚合工程（Spring Boot 3.5.16、Java 21、MyBatis-Plus 3.5.18、达梦 DM8、Redis + Redisson、RabbitMQ、MinIO 可选），模块为 `mall-common`、`mall-api-order`、`mall-api-payment`、`mall-api-user`、`mall-api-cart`、`mall-api-product`、`mall-api-refund`、`mall-infra`（锁 / Outbox / MQ 公共实现 / Feign 公共配置 jar 库）、`mall-core`（两个 BFF 使用的远程适配器与共享配置 jar 库）、`mall-user-service`（用户领域实现）、`mall-cart-service`（购物车领域实现）、`mall-product-service`、`mall-order-service`（订单与物流领域实现）、`mall-payment-service`（支付领域实现）、`mall-refund-service`（退款领域实现）、`mall-bill-service`（账单核对领域实现）、`mall-user-bff`、`mall-admin-bff`、`mall-gateway`。
- `user-ui/`：React 18 + Vite 5 + Ant Design 5，用户商城，开发端口 `3000`。
- `admin-ui/`：React 18 + Vite 5 + Ant Design 5 + SheetJS + Luckysheet，管理后台，开发端口 `3002`。
- 后端对外入口：`backend/mall-gateway` 占用 `8080`（Spring Cloud Gateway，路由 + CORS）；`mall-user-bff` 默认 `8081`，`mall-admin-bff` 默认 `8082`，`mall-user-service` 默认 `8083`、`mall-cart-service` 默认 `8089`、`mall-product-service` 默认 `8084`、`mall-order-service` 默认 `8085`、`mall-payment-service` 默认 `8086`、`mall-refund-service` 默认 `8087`、`mall-bill-service` 默认 `8088`，后七者仅供服务间调用与本地排障。两个前端仍通过 CORS 访问 `http://localhost:8080`，当前无 Vite 代理。
- 网关分流：退款申请/通知/查询与订单取消（`/api/refund-applies/**`、`/api/refund-info/**`、`/api/wx-pay/refunds`、`/api/wx-pay/refunds/**`、`/api/wx-pay/query-refund/**`、`/api/ali-pay/trade/refund`、`/api/ali-pay/trade/refund/**`、`/api/ali-pay/trade/fastpay/refund/**`、`/api/admin/refund/**`、`/api/order/*/cancel`）→ `mall-refund-service` 且必须排在 admin/order/payment 路由之前；`/api/admin/order/**` → `mall-order-service` 且必须排在 `/api/admin/**` 之前；`/api/reconciliation/**` → `mall-bill-service` 且必须排在 `/api/admin/**` 之前；`/api/admin/**` → `mall-admin-bff`；`/api/wx-pay/**`、`/api/wx-pay-v2/**`、`/api/ali-pay/**`、`/api/payment-*/**` → `mall-payment-service`；`/api/checkout/**`、`/api/order-info/**`、`/api/order/**` → `mall-order-service`；其余 `/api/**` → `mall-user-bff`；`/internal/**` 不经过网关，仅供服务间调用。Controller 归各自 BFF 或领域服务；`mall-core` 只保留两个 BFF 使用的用户/商品/购物车/支付远程适配器与共享配置，用户领域实现独立在 `mall-user-service`，购物车领域实现独立在 `mall-cart-service`，商品与库存领域实现独立在 `mall-product-service`，订单与物流领域实现独立在 `mall-order-service`，支付领域实现独立在 `mall-payment-service`，退款领域实现独立在 `mall-refund-service`，账单核对领域实现独立在 `mall-bill-service`。用户表和 Redis 用户会话只由用户服务写入，购物车表（`t_cart_item`）只由购物车服务写入，商品/库存表只由商品服务写入，订单/明细/物流表（`t_order_info`、`t_order_item`、`t_order_shipment`）与 `t_order_request` 只由订单服务写入，`seata_state_machine_def`、`seata_state_machine_inst`、`seata_state_inst` 由订单服务内嵌的 Seata 状态机引擎读写，支付/成交/支付配置表只由支付服务写入，退款表（`t_refund_info`、`t_refund_item`、`t_refund_order`）只由退款服务写入，账单表（`t_bill_import`、`t_bill_record`、`t_bill_reconcile_discrepancy`）只由账单服务写入，BFF 不持有独立写权限。
- 后台任务（`@Scheduled` / `@RabbitListener` / Outbox 扫描）按领域归属执行：订单关单消费、支付事件消费、超时关单扫描、物流模拟、送达超时自动确认收货与下单幂等记录清理在 `mall-order-service`；退款冲正消费、退款状态同步消费与 `RefundStatusSyncScheduler` 在 `mall-refund-service`；Outbox 中继只由 `mall-user-bff` 执行。`mall-order-service` 开启 `@EnableScheduling` / `@EnableRabbit`；`mall-admin-bff`、`mall-payment-service` 与 `mall-bill-service` 都不开启 `@EnableScheduling` / `@EnableRabbit`，并配置 `spring.rabbitmq.listener.simple.auto-startup=false`；`mall-payment-service`、`mall-refund-service` 与 `mall-bill-service` 配置 `local-message.relay-enabled=false`。`mall-user-service`、`mall-cart-service`、`mall-product-service`、`mall-order-service`、`mall-payment-service`、`mall-refund-service`、`mall-bill-service` 不导入 `application-core.yml`，除 user-bff 外都不执行 Outbox 扫描。
- 后端按 8 个限界上下文组织：`shared`、`product`、`order`、`payment`、`refund`、`user`、`cart`、`bill`；P3–P7 阶段用户服务、商品服务、订单服务、支付服务、退款服务、账单服务与其他进程仍同库同 schema。
- 每个业务上下文遵循四层结构：`interfaces / application / domain / infrastructure`。
- 服务治理：所有可运行进程通过 Nacos 注册服务并从配置中心导入 `<应用名>.yaml`（`spring.config.import: optional:nacos:...`）；内部调用统一使用 Spring Cloud OpenFeign（`cc.ivera.infra.feign.InternalFeignConfiguration` 解包 `R` 信封），网关路由目标统一为 `lb://mall-xxx-service`。`NACOS_ENABLED=false` 时注册与配置导入关闭，此时必须用 `USER_SERVICE_BASE_URL` 等 base-url 与 `MALL_*_URI` 直连。微信/支付宝渠道客户端保持独立 SDK/HTTP 客户端，不得改成 Feign。
- 后端已恢复最小测试基线：`mall-refund-service` 的 `RefundPolicyTest`（退款尾差与防超退不变量）与 `RefundDomainArchitectureTest`（ArchUnit 校验 domain 依赖方向），以及 `mall-order-service` 的 `AutoReceiptSchedulerTest`（自动收货链路复用与单批失败隔离）；其余模块仍无测试，验证以编译通过为准。`user-ui` 有 1 个 Node 逻辑测试；`admin-ui` 暂无自动化测试。CI 见 `.github/workflows/ci.yml`。

文档职责：

- `README.md`：面向使用者，说明项目能力、技术栈、启动方式、配置与测试；第 15 章面向开发者，说明架构、领域边界、数据模型、交易链路、并发与 MQ 设计。
- `backend/docs/DAMENG_DM8_OPERATIONS.md`：DM8 运维。
- `backend/docs/RABBITMQ_OPERATIONS.md`：RabbitMQ 拓扑、迁移、失败队列、冒烟与回滚。

## 2. 修改前先定性

任何改动开始前都应归类；不明确时按“设计变更”处理。

| 类型 | 判定 | 必须动作 |
|---|---|---|
| 局部缺陷 | 既有契约明确，仅单点行为错误 | 先补复现测试，再做最小修复，跑受影响测试和全量回归 |
| 设计变更 | 影响工作流、状态机、领域模型、架构边界、配置来源、MQ 拓扑、支付渠道行为 | 明确“现状 → 目标 → 影响面 → 回滚”，同步 `README.md` 第 15 章 |
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
├── interfaces       REST Controller / MQ Consumer / Scheduler
├── application      用例编排、事务边界、应用服务
├── domain           领域模型、枚举、策略、repository/gateway 端口
└── infrastructure   PO / Mapper / Converter / RepositoryImpl / 外部适配 / MQ 拓扑
```

必须遵守：

- `domain` 不得依赖 Spring、MyBatis-Plus 或具体 infrastructure 实现。
- 请求/响应 DTO/VO 归属 `application.dto` / `application.vo`；`application` 不得反向依赖 `interfaces`（代码评审强制）。
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
- 确认收货（用户手动或送达超时自动）：`locked → sold`。
- 已发货未收货 `REFUND_ONLY`：管理员受理前必须确认商品去向；`LOST` 执行 `locked → lost`，`RECOVERED` 执行 `locked → available`，不得在未判定时直接发起渠道退款。
- 已收货 `REFUND_ONLY`：`sold → lost`。
- `RETURN_AND_REFUND`：必须在退货签收/质检后才可 `sold → available`。
- 差价退款、重复支付、晚到支付冲正：不回补库存。

### 5.3 支付成交唯一性

- 一个业务订单允许 `1:N` 本地支付尝试（`t_payment_order`）。
- 同渠道活跃支付单优先复用；跨渠道可以有不同支付尝试。
- 一个业务订单只允许一笔真正有效的 `SUCCESS` 成交，权威事实是 `t_payment_deal`：`payment_no` 与 `order_no` 双唯一。
- 支付成交只做“支付单 CAS + 写成交事实 + 关闭其它活跃支付尝试 + 发事件”，**不得直接修改订单状态**；订单推进由订单侧消费 `PAYMENT_SUCCEEDED` 完成。
- 关单 / 取消前必须先查成交事实，已成交则标记支付，禁止“已收款却本地关单”。
- 不同 `paymentNo` 的真实重复支付发 `DUPLICATE_PAYMENT_DETECTED`；订单已关闭/取消后成交发 `PAYMENT_LATE_ARRIVED`；退款侧按 `paymentNo` 建冲正单，**冲正不改库存**。
- 跨上下文消费必须用 `t_consumer_dedupe(consumer, event_id)` 去重，且保持“业务 → 去重回写 → `basicAck`”顺序。

### 5.4 退款防超退

- 金额与数量校验以服务端 `refund.domain.policy.RefundPolicy` 为准。
- 退款受理时冻结可退额度，禁止先退款后补校验。
- 用户端前置计算仅用于体验，不得取代后端最终核算。
- 最后一件商品允许按服务端规则处理金额尾差。
- `order_item.restocked_qty` 的累加守卫属于退款调用方：退款受理时先调用 `OrderRefundQuotaApi.addRestockedQty` 做 CAS 守卫，再调用商品服务的回补/核销接口；商品服务不得反向读取订单表判断可回补上限。
- 渠道退款唯一入口是 `cc.ivera.api.payment.PaymentChannelRefundApi`；`payment` 上下文不得引用退款仓储、`RefundInfoService` 或写 `t_refund_*`，退款成功/处理中/失败由退款域回写。
- 发起渠道退款必须与退款状态推进处于同一把分布式锁内，禁止“事务提交后已释放锁再调渠道”，避免同一退款单被并发重复提交渠道。

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
Redis / Redisson 分布式锁（watchdog 自动续期 + fence 围栏令牌）
        +
CAS 条件更新（含 fence_token <= newToken 围栏 CAS）
        +
数据库唯一约束 / biz_no 幂等
```

禁止将关键状态机改造成无条件 UPDATE。

**禁止引入 `select ... for update` 作为本项目的并发主方案。** 当前项目的并发权威是分布式锁 + CAS；远程支付渠道调用期间不得长时间持有数据库行锁。

**分布式锁机制**：`DistributedLockTemplate.execute(lockKey, waitTimeMillis, supplier)` 统一使用 Redisson watchdog（`leaseTime = -1`，持有期间自动续期，调用方无需指定过期时间）。加锁成功后通过 Redis `INCR lock:fence:seq` 生成**全局**单调递增围栏令牌，写入 `FenceTokenHolder`（ThreadLocal）。令牌必须全局递增：同一聚合会被不同业务键的锁先后推进（支付→发货→收货→退款受理），按锁键各自计数会让新锁拿到的令牌小于行上已有令牌，导致合法 CAS 被静默丢弃。

**Fence 围栏令牌**：核心聚合表（`t_order_info`、`t_payment_order`、`t_refund_order`、`t_refund_info`、`t_bill_import`）均含 `fence_token BIGINT DEFAULT 0` 列。状态推进 UPDATE 在持锁时追加 `SET fence_token = #{fenceToken}` 与 `WHERE ... AND fence_token <= #{fenceToken}`，确保锁过期（异步复制延迟、进程停顿、GC 等）后旧持有者的写入被新持有者的更高令牌拒绝；同一把锁内允许连续多次推进（首次 UPDATE 已把行令牌写成当前令牌，用严格 `<` 会让同锁内的第二次更新静默失效）。未持锁时 `FenceTokenHolder.get()` 返回 null，围栏 CAS 自动跳过，不影响无锁场景的正常更新。

### 5.7 购物车持久化

- 购物车唯一权威存储是 DM8 `t_cart_item`，只由 `mall-cart-service` 读写；`mall-core` 只保留 `CartServiceRemoteAdapter`，禁止恢复 Redis 购物车实现或 Redis/DB 双写。
- 加购使用 `MERGE INTO` 原子 upsert，由唯一键 `uk_cart_user_product (user_id, product_id)` 兜底并发重复插入。
- `selected` 以 `CHAR(1)` 的 `'0'/'1'` 存储，null 视为已勾选，保持与历史行为一致。
- 购物车不冗余商品字段，商品标题/价格/库存/状态在读取时通过商品服务 `ProductQueryApi` 实时获取。

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

支付业务事件复用同一套 MANUAL ACK + Failure DLQ 语义，但使用独立拓扑：

```text
payment.event.exchange
  ├─ payment.succeeded → payment.succeeded.order.queue
  ├─ payment.duplicate → payment.duplicate.refund.queue
  └─ payment.late      → payment.late.refund.queue
        ↓ 重试耗尽（x-dead-letter-exchange=payment.event.failure.exchange）
  payment.event.failure.exchange
        ├─ payment.succeeded.failure.queue
        ├─ payment.duplicate.failure.queue
        ├─ payment.late.failure.queue
        └─ payment.event.parking.queue
```

必须保持以下职责边界：

- `Failure Queue`：保留最终消费失败消息，供排障、审计、人工重放。
- `Parking Lot Queue`：人工长期隔离 poison message，不配置自动 Consumer，不形成自动循环。
- `TimeoutOrderCloseScheduler` / `RefundStatusSyncScheduler`：DB 驱动的最终一致性兜底，与 Failure Queue 并行存在；订单扫描周期由 `payment.order.timeout-scan-ms` 控制，退款扫描周期由 `payment.refund.status-sync-scan-ms` 独立控制。单笔失败只能记录日志并等待后续收敛，不得中断同批次其它记录。
- `t_local_message`：Transactional Outbox，负责“业务事务落库后可靠投递”，不是消费失败队列的替代品。
- 后台任务与 MQ 消费者按领域归属运行：订单关单、支付事件消费、超时扫描、物流模拟、送达超时自动确认收货与下单幂等记录清理在 `mall-order-service`，退款冲正、退款状态同步消费与退款扫描在 `mall-refund-service`，Outbox 中继只由 `mall-user-bff` 执行；下单 Saga 由 Seata 状态机同步驱动，不是定时任务。禁止在 `mall-admin-bff` 或 `mall-payment-service` 开启，避免重复消费与重复扫描。`mall-payment-service` 只声明支付事件拓扑，不消费队列、不扫描 `t_local_message`；`mall-refund-service` 与 `mall-order-service` 消费各自队列但配置 `local-message.relay-enabled=false`。

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

后端已恢复最小测试基线：`mall-refund-service` 的 `RefundPolicyTest` 与 `RefundDomainArchitectureTest` 均为纯域单测，`mall-order-service` 的 `AutoReceiptSchedulerTest` 为 Mockito 单测；均不启动 Spring 上下文、不依赖 DM8，其余模块仍无测试。变更验证以 `mvn clean test` 通过为准。CI（`.github/workflows/ci.yml`）执行同一命令，并构建两个前端。

必须执行的回归：

```powershell
cd backend
mvn clean test
```

`user-ui` 保留 Node 逻辑测试；触碰前端页面时还应执行对应构建：

```powershell
cd ../user-ui
npm run test:logic
npm run build
```

`admin-ui` 当前没有逻辑测试，改动时至少执行 `npm run build`。

## 9. 文档同步规则

下列变化必须同步 `README.md` 第 15 章：

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
- [ ] `mvn clean test`（编译）通过。
- [ ] 需要时 `user-ui` / `admin-ui` 构建通过。
- [ ] `README.md` / 运维文档与代码一致。
- [ ] 没有无关代码、无关格式化或隐式行为变化。
