# 电商商城平台

> 一个围绕真实交易链路设计的前后端分离商城项目，覆盖商品、购物车、订单、库存、微信/支付宝支付、履约、退款、账单对账、用户与后台管理，并以 DDD、CAS、分布式锁、Transactional Outbox、RabbitMQ Failure DLQ / Parking Lot 等机制保证并发与最终一致性。

## 1. 项目概览

项目由 3 个工程组成：

- `backend/`：Spring Boot 2.3.7 + Java 8 后端。
- `user-ui/`：React 18 用户商城，开发端口 `3000`。
- `admin-ui/`：React 18 管理后台，开发端口 `3002`。

后端按 8 个限界上下文组织：

```text
shared / product / order / payment / refund / user / cart / bill
```

每个业务上下文采用统一四层：

```text
interfaces → application → domain ← infrastructure
```

完整架构和代码导览见 [CODE_INTRO.md](CODE_INTRO.md)。开发规范见 [AGENTS.md](AGENTS.md)。

## 2. 业务能力

### 2.1 用户商城

- 用户注册、登录、退出、Access/Refresh 双 Token 刷新。
- 登录失败保护：同一用户名连续输错密码达到阈值后短时锁定。
- 商品列表、商品库存展示。
- Redis 购物车。
- 收货地址 CRUD、默认地址、三级行政区划级联。
- 多商品结算、订单快照价、订单查询。
- 微信支付 V3 Native 扫码支付、JSAPI 能力、回调、主动查单、退款、账单相关接口。
- 微信支付 V2 Native 兼容链路。
- 支付宝支付、通知、查单、关单、退款、账单下载地址。
- 发货/物流状态查看、送达后确认收货。
- 已付款未发货取消订单。
- 分项退款、仅退款、退货退款、退款申请编辑/撤销。
- 支付成功页和退款申请列表。

### 2.2 管理后台

- 订单列表、订单详情、状态筛选。
- 待发货订单、模拟发货。
- 强制关单、渠道查单、标记线下已付款。
- 支付尝试记录查看。
- 商品 CRUD、上下架、单个/批量库存调整。
- 库存流水分页与审计。
- Excel 库存导入、编辑、确认入库。
- Excel 文件支持本地磁盘或 MinIO 存储。
- 退款受理、拒绝、退货签收、渠道重试、状态同步、差价退款。
- 用户列表、启用/禁用、用户详情。
- 密码重置申请受理/拒绝、管理员直接重置密码。
- 支付渠道和支付应用配置维护、缓存刷新。
- 微信账单 CSV 上传、同步对账、差异查看、重跑、人工标记处理。

## 3. 交易系统核心设计

### 3.1 库存四桶模型

`t_product` 使用四桶库存：

```text
available_stock  可用
locked_stock     锁定
sold_stock       已售
lost_stock       货损
```

核心恒等式：

```text
available + locked + sold + lost = 入库总量
```

典型流转：

```text
下单：       available → locked
支付成功：   预占状态 LOCKED → COMMITTED，库存数量不变
关单/取消：  locked → available
确认收货：   locked → sold
仅退款：     locked/sold → lost
退货退款：   退货签收后 sold → available
```

防超卖由 `ProductMapper.reserveStock` 条件 UPDATE 作为根闸门：库存不足时整个下单事务回滚，不生成有效订单。

所有库存变化都写 `t_inventory_transaction`，并使用 `biz_no` + 数据库唯一约束保证幂等。

### 3.2 本地订单与支付单

业务订单与渠道支付尝试为 `1:N`：

```text
t_order_info
    └── 1:N t_payment_order
```

规则：

- 同渠道已有活跃支付单时优先复用。
- 跨渠道可以创建不同支付尝试。
- 一个业务订单最终只允许一笔有效成交。
- 首笔成交后关闭其它活跃支付尝试。
- 重复支付、关单后晚到支付走自动冲正退款，不回补库存。

### 3.3 并发与幂等

关键交易路径采用：

```text
通知去重（部分渠道回调）
        ↓
Redisson 分布式锁
        ↓
CAS 条件 UPDATE
        ↓
数据库唯一约束 / biz_no
```

项目不使用 `select ... for update` 作为关键并发控制方案。订单、支付、退款等状态推进以分布式锁 + CAS 为主。

### 3.4 退款

退款覆盖：

- 未发货取消。
- 退货退款。
- 仅退款。
- 差价退款。
- 重复支付自动冲正。
- 晚到支付自动冲正。

退款额度由服务端 `RefundPolicy` 最终核算，受理阶段冻结可退金额/数量，避免并发超退。

库存处理严格区分资金与货权：

- 未发货取消：可回补锁定库存。
- 退货退款：退货签收/质检后回补。
- 仅退款不退货：转入 `lost_stock`，不进入可售库存。
- 差价退款与系统资金冲正：不动库存。

## 4. RabbitMQ 与最终一致性

项目当前使用 `AUTO ACK`，不是手写 `MANUAL ACK`：

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: auto
        default-requeue-rejected: false
        retry:
          enabled: true
          max-attempts: 4
          initial-interval: 2000
          multiplier: 3.0
          max-interval: 20000
```

消费语义：

```text
业务成功
→ markConsumed
→ Listener 正常返回
→ Spring AUTO ACK

业务异常
→ 异常向外抛出
→ Spring Retry
→ 重试耗尽 Reject
→ Release Queue DLX
→ Failure Queue
```

订单超时关单和退款状态同步均采用独立失败拓扑：

```text
Event Exchange
    ↓
Delay Queue
    ↓ TTL
Dead Letter Exchange
    ↓
Release Queue
    ↓
Consumer
    ├─ success → AUTO ACK
    └─ retry exhausted
          ↓
     Failure Exchange
          ↓
     Failure Queue
          ↓
     人工排障 / 重放
          ↓
     Parking Lot Queue（可选人工隔离）
```

Parking Lot Queue 不配置自动消费者，不自动回灌，避免 poison message 形成失败循环。

此外还有两层保障：

- `t_local_message` Transactional Outbox：业务事务内先落 `PENDING`，事务提交后发消息，broker confirm 后置 `SENT`，消费完成后置 `CONSUMED`。
- DB 兜底调度：`TimeoutOrderCloseScheduler`、`RefundStatusSyncScheduler` 独立扫描数据库状态，保证即使 MQ 故障仍能最终收敛。

详细拓扑、迁移、Failure Queue 处理与回滚见 [backend/docs/RABBITMQ_OPERATIONS.md](backend/docs/RABBITMQ_OPERATIONS.md)。

### 4.1 RabbitMQ 升级注意

如果旧环境已存在以下 Release Queue：

```text
payment.order.close.release.queue
payment.refund.status-sync.release.queue
```

当前版本给它们新增了 `x-dead-letter-exchange` 和 `x-dead-letter-routing-key`。RabbitMQ Queue arguments 无法原地修改，首次升级必须按运维文档执行：

1. 停止对应消费者/应用。
2. 确认 Release Queue 内无需要保留的未处理消息，必要时先备份/转存。
3. 删除旧 Release Queue。
4. 启动应用，让 Spring AMQP 按新参数重新声明。
5. 检查 Failure Exchange / Failure Queue / Parking Lot Queue binding。

否则可能出现 `PRECONDITION_FAILED`。

## 5. 账单对账

管理员上传微信交易账单 CSV 后，系统同步解析并对本地支付/退款记录进行核对，不走 MQ。

支持账单类型：

| 类型 | 内容 | 对账范围 |
|---|---|---|
| `ALL` | 支付 + 退款 + 撤销 | 支付与退款 |
| `SUCCESS` | 支付成功 | 支付 |
| `REFUND` | 退款 | 退款 |

差异类型共 8 类：

```text
PAY_CHANNEL_ONLY
PAY_LOCAL_ONLY
PAY_AMOUNT_MISMATCH
PAY_STATUS_MISMATCH
REFUND_CHANNEL_ONLY
REFUND_LOCAL_ONLY
REFUND_AMOUNT_MISMATCH
REFUND_STATUS_MISMATCH
```

幂等防线包括文件 SHA-256、账单日/种类互斥、Redisson 锁、数据库唯一约束、批次状态校验。

## 6. 技术栈

| 分类 | 技术 | 版本/说明 |
|---|---|---|
| Java | Java | 8 |
| 后端 | Spring Boot | 2.3.7.RELEASE |
| ORM | MyBatis-Plus | 3.3.1 |
| 数据库 | 达梦 DM8 / DmJdbcDriver18 | 8.1.2.192 |
| Redis | Spring Data Redis + Redisson | Redisson 3.16.8 |
| MQ | RabbitMQ + Spring AMQP | Spring Boot Starter AMQP |
| 对象存储 | MinIO | 8.5.7，可选 |
| 微信 V3 | wechatpay-apache-httpclient | 0.3.0 |
| 微信 V2 | wxpay-sdk | 0.0.3 |
| 支付宝 | alipay-sdk-java | 4.22.57.ALL |
| API 文档 | Springfox Swagger | 2.7.0 |
| 用户前端 | React + Vite + Ant Design | React 18 / Vite 5 / antd 5 |
| 管理前端 | React + Vite + Ant Design + xlsx + Luckysheet | React 18 |

## 7. 项目结构

```text
mall/
├── backend/
│   ├── src/main/java/cc/ivera/
│   │   ├── shared/          # 共享内核、认证、锁、Outbox、公共 Web 能力
│   │   ├── product/         # 商品、库存、库存流水、Excel 导入
│   │   ├── order/           # 订单、结算、发货、物流、关单
│   │   ├── payment/         # 微信/支付宝、支付单、支付记录、支付配置
│   │   ├── refund/          # 退款单、退款明细、退款策略、渠道退款同步
│   │   ├── user/            # 用户、认证、地址、行政区划、密码重置
│   │   ├── cart/            # Redis 购物车
│   │   └── bill/            # 微信账单上传与对账
│   ├── src/main/resources/
│   │   ├── application.yml
│   │   └── mapper/
│   ├── src/test/java/       # 20 个测试类、129 个后端用例
│   ├── docs/
│   │   ├── DAMENG_DM8_OPERATIONS.md
│   │   └── RABBITMQ_OPERATIONS.md
│   └── env/
│       ├── docker-compose.dm8.yml
│       └── sql/dm8/schema.sql
├── user-ui/
│   ├── src/
│   └── tests/refresh-single-flight.test.mjs
├── admin-ui/
│   └── src/
├── AGENTS.md
├── CODE_INTRO.md
└── README.md
```

## 8. 数据库

统一建表脚本：

```text
backend/env/sql/dm8/schema.sql
```

当前脚本创建 22 张业务表：

```text
t_payment_channel
t_payment_app
t_order_info
t_payment_info
t_product
t_refund_info
t_user
t_password_reset_request
t_order_item
t_refund_order
t_refund_item
t_payment_order
t_inventory_reservation
t_inventory_transaction
t_stock_import
t_order_shipment
t_bill_import
t_bill_record
t_bill_reconcile_discrepancy
t_local_message
t_region
t_shipping_address
```

脚本是当前数据库结构的唯一权威版本，重复执行前会处理既有对象，效果接近清库重建。生产环境执行前务必备份数据并评估 DROP 行为。

## 9. 环境要求

建议：

- JDK 8。
- Maven 3.6+。
- Node.js 18+。
- Docker / Docker Compose（DM8 容器）。
- Redis 5.0+。
- RabbitMQ 3.7+。
- MinIO：仅当 `stock.import.storage=minio` 时需要。

## 10. 快速启动

### 10.1 启动 DM8

```powershell
cd backend\env
docker compose -f docker-compose.dm8.yml up -d
```

容器健康后，用达梦客户端连接数据库并执行：

```text
backend/env/sql/dm8/schema.sql
```

开发脚本中包含初始化管理员种子账号，生产部署前必须替换默认凭据。

### 10.2 配置后端

主要配置文件：

```text
backend/src/main/resources/application.yml
```

重点配置：

| 配置 | 说明 |
|---|---|
| `spring.datasource.*` | DM8 连接；支持 `DM_HOST/DM_PORT/DM_SCHEMA/DM_USERNAME/DM_PASSWORD` |
| `spring.redis.*` | Redis |
| `spring.rabbitmq.*` | RabbitMQ、publisher confirm/returns、listener retry/AUTO ACK |
| `payment.auth.*` | Access/Refresh TTL、JWT Secret |
| `payment.order.expire-minutes` | 未支付订单超时与延迟关单 TTL |
| `payment.refund.status-sync-delay-ms` | 退款状态同步延迟与兜底周期 |
| `stock.import.storage` | `local` / `minio` |
| `stock.import.dir` | 本地 Excel 存储目录 |
| `stock.import.minio.*` | MinIO endpoint/凭据/bucket |

生产环境不要直接沿用仓库中的开发凭据、JWT Secret、数据库密码、Redis 密码、MinIO 凭据或支付密钥。

### 10.3 启动后端

```powershell
cd backend
mvn spring-boot:run
```

访问：

```text
API:     http://localhost:8080
Swagger: http://localhost:8080/swagger-ui.html
```

### 10.4 启动用户商城

```powershell
cd user-ui
npm install
npm run dev
```

访问：

```text
http://localhost:3000
```

主要路由：

```text
/
/login
/cart
/orders
/refund-applications
/account
/addresses
/success
```

### 10.5 启动管理后台

```powershell
cd admin-ui
npm install
npm run dev
```

访问：

```text
http://localhost:3002
```

主要路由：

```text
/admin/orders
/admin/shipping
/admin/products
/admin/refunds
/admin/users
/admin/reset-requests
/admin/reset-password
/admin/stock-maintenance
/admin/stock-edit/:id
/admin/download
/admin/payment-config
/admin/reconciliation
```

## 11. 测试与构建

### 后端

当前基线：**20 个测试类、129 个用例**。

```powershell
cd backend
mvn test
```

测试覆盖：

- 领域聚合状态机与守卫。
- `Money` 值对象。
- `RefundPolicy` 退款额度。
- PO ↔ Domain Converter。
- 微信账单 CSV Parser。
- 支付成功、退款、购物车应用服务。
- 登录失败保护。
- Order Close / Refund Sync Consumer 失败传播契约。
- Release Queue Failure DLX / Failure Queue / Parking Lot Queue 拓扑。

### 用户前端

```powershell
cd user-ui
npm run test:logic
npm run build
```

`test:logic` 当前验证并发 401 下 Refresh Token single-flight。

### 管理后台

```powershell
cd admin-ui
npm run build
```

当前 `admin-ui` 暂无独立自动化逻辑测试。

## 12. 重要接口分组

后端当前有 24 个 Controller，主要前缀如下：

| 前缀 | 功能 |
|---|---|
| `/api/auth` | 登录、注册、刷新、退出、密码 |
| `/api/product` | 商城商品 |
| `/api/cart` | 购物车 |
| `/api/checkout` | 结算、下单、支付入口 |
| `/api/order` / `/api/order-info` | 订单、履约、查询 |
| `/api/refund-applies` / `/api/refund-info` | 退款申请/退款查询 |
| `/api/wx-pay` | 微信支付 V3 |
| `/api/wx-pay-v2` | 微信支付 V2 |
| `/api/ali-pay` | 支付宝 |
| `/api/payment-app` | 支付应用 |
| `/api/payment-channel` | 支付渠道配置 |
| `/api/payment-config` | 支付配置缓存 |
| `/api/regions` | 行政区划 |
| `/api/user/address` | 用户地址 |
| `/api/admin/order` | 管理端订单/发货 |
| `/api/admin/products` | 管理端商品库存 |
| `/api/admin/stock` | 库存流水/Excel 导入 |
| `/api/admin/refund` | 管理端退款 |
| `/api/admin/users` | 用户管理 |
| `/api/admin/password-reset-requests` | 密码重置申请 |
| `/api/reconciliation` | 对账 |

详细路由和代码位置见 [CODE_INTRO.md](CODE_INTRO.md)。

## 13. 文档索引

- [AGENTS.md](AGENTS.md)：编码规范、领域不变量、测试与 DoD。
- [CODE_INTRO.md](CODE_INTRO.md)：系统架构、领域模型、数据表、交易链路、MQ、接口与前端导览。
- [backend/docs/DAMENG_DM8_OPERATIONS.md](backend/docs/DAMENG_DM8_OPERATIONS.md)：DM8 部署和初始化。
- [backend/docs/RABBITMQ_OPERATIONS.md](backend/docs/RABBITMQ_OPERATIONS.md)：RabbitMQ 拓扑、Failure Queue、Parking Lot、迁移和回滚。

## 14. 当前设计原则

这个项目将资金、库存、订单状态和消息可靠性分开治理：

```text
资金正确性：渠道查询 + 支付/退款状态机 + 冲正
库存正确性：四桶模型 + 条件 UPDATE + biz_no
并发正确性：Redisson 锁 + CAS + 唯一约束
消息可靠性：Outbox + publisher confirm + AUTO ACK + Retry + Failure DLQ
最终一致性：DB Scheduler 独立兜底
```

修改核心交易代码前，请先阅读 [AGENTS.md](AGENTS.md) 和 [CODE_INTRO.md](CODE_INTRO.md)。
