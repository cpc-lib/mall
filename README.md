# 电商商城平台

> 一个围绕真实交易链路设计的前后端分离商城项目，覆盖商品、购物车、订单、库存、微信/支付宝支付、履约、退款、账单对账、用户与后台管理，并以 DDD、CAS、分布式锁、Transactional Outbox、RabbitMQ Failure DLQ / Parking Lot 等机制保证并发与最终一致性。

## 1. 项目概览

项目由 3 个工程组成：

- `backend/`：Spring Boot 3.5.16 + Java 21 后端。
- `user-ui/`：React 18 用户商城，开发端口 `3000`；仅面向手机端适配，宽屏浏览器中保持最大 480px 的手机画布，不提供桌面版布局。
- `admin-ui/`：React 18 管理后台，开发端口 `3002`。

服务治理使用 Nacos 做服务注册与配置中心；进程间内部调用统一走 Spring Cloud OpenFeign（直连地址可覆盖），网关按 `lb://` 服务名路由。

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
- 会话治理：一次登录一个会话（`sid`），支持按设备查看、踢单设备、踢全部；退出只下线当前设备。
- 登录失败保护：同一用户名连续输错密码达到阈值后短时锁定。
- 商品列表、商品库存展示。
- 购物车（DM8 `t_cart_item`，由购物车服务统一持久化）。
- 收货地址 CRUD、默认地址、三级行政区划级联。
- 多商品结算、订单快照价、订单查询。
- 微信支付 V3 Native 扫码支付、JSAPI 能力、回调、主动查单、退款、账单相关接口。
- 微信支付 V2 Native 兼容链路。
- 支付宝支付、通知、查单、关单、退款、账单下载地址。
- 发货/物流状态查看、送达后确认收货。
- 已付款未发货取消订单。
- 分项退款、仅退款、退货退款、退款申请编辑/撤销；已发货未收货退款由管理员确认商品丢失或已全部回收后再发起渠道退款。
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
- 退款受理、拒绝、已发货商品去向判定（丢失/全部回收）、退货签收、渠道重试、状态同步、差价退款。
- 用户列表、启用/禁用、用户详情。
- 密码重置申请受理/拒绝、管理员直接重置密码。
- 支付渠道和支付应用配置维护、缓存刷新。
- 微信交易账单 XLSX 上传、同步对账、差异查看、重跑、人工标记处理。

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
已发货退款： 管理员判定丢失 → locked → lost
             管理员确认全部回收 → locked → available
已收货仅退款：sold → lost
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
- 一个业务订单最终只允许一笔有效成交，由 `t_payment_deal` 的 `order_no` / `payment_no` 双唯一键兜底。
- 首笔成交后关闭其它活跃支付尝试并对订单发 `PAYMENT_SUCCEEDED` 事件。
- 重复支付、关单后晚到支付以 `DUPLICATE_PAYMENT_DETECTED` / `PAYMENT_LATE_ARRIVED` 事件通知退款侧自动冲正，不回补库存。

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
- 已发货未收货 `REFUND_ONLY`：管理员受理时必须确认商品最终去向；`LOST` 执行 `locked → lost`，`RECOVERED` 执行 `locked → available`，判定完成后才发起渠道退款。
- 已收货 `REFUND_ONLY`：商品不退回，执行 `sold → lost`。
- `RETURN_AND_REFUND`：退货签收/质检后执行 `sold → available`，再发起渠道退款。
- 差价退款与系统资金冲正：不动库存。

## 4. RabbitMQ 与最终一致性

项目当前使用 `MANUAL ACK`：只有业务处理与本地消息状态回写成功后才显式确认消息。

```yaml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual
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
→ Channel.basicAck(deliveryTag, false)

业务 / markConsumed / basicAck 异常
→ 异常向外抛出
→ Spring Retry
→ 重试耗尽 RejectAndDontRequeue
→ Release Queue DLX
→ Failure Queue
```

这是 **at-least-once** 语义：如果业务与 `markConsumed` 已成功，但 broker ACK 因连接故障失败，消息可能再次投递；现有订单/退款 CAS、分布式锁与 `biz_no` 幂等必须继续保留，用来安全承接重复消费。

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
    ├─ success → MANUAL basicAck
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
- 支付业务事件（`payment.succeeded` / `payment.duplicate` / `payment.late`）由 `payment.event.exchange` 投递，订单与退款侧用 `t_consumer_dedupe` 按 `event_id` 去重；失败进入独立的 `payment.event.failure.exchange` 与 Parking Lot。
- DB 最终一致性兜底：`TimeoutOrderCloseScheduler` 扫描超时 `NOTPAY` 订单并重新走渠道反查/关单链路，`RefundStatusSyncScheduler` 扫描 `APPROVED + PROCESSING` 退款并重新走 `queryRefundStatus`。两者均按“单条失败不阻塞整批”执行，即使 MQ 不可用、Retry 耗尽进入 Failure Queue 或消息未及时消费，数据库状态仍会持续收敛。

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

## 5. 账账核对

管理员上传微信支付下载的交易账单 XLSX（`.xlsx`）后，系统同步执行“渠道交易账 vs 平台交易账”的双向账账核对，不走 MQ。前端和后端都只接受 `.xlsx`，后端使用 Apache POI 读取真实 Excel 工作簿，不把 CSV 改后缀伪装成 XLSX：

```text
渠道账：t_bill_record（微信交易账单）
              ↕
       双向逐笔 + 汇总平衡
              ↕
平台账：t_payment_order + t_refund_order
```

支付账以 `PaymentOrder` 为权威账本，保留“订单 1:N 支付尝试”语义；优先按 `channel_order_no ↔ 微信订单号` 逐笔匹配，`order_no` 仅作辅助定位，禁止按订单号聚合后核对。退款账以 `RefundOrder.refund_no` 为主匹配键。

日切口径统一为 `Asia/Shanghai`：支付按 `paid_time` 归账，退款按 `success_time` 归账，避免创建时间、申请时间或服务器默认时区造成跨日假差异。

支持账单类型：

| 类型 | 内容 | 对账范围 |
|---|---|---|
| `ALL` | 支付 + 退款 + 撤销 | 支付与退款 |
| `SUCCESS` | 支付成功 | 支付 |
| `REFUND` | 退款 | 退款 |

差异覆盖单边账、金额/状态、串单和重复流水：

```text
PAY_CHANNEL_ONLY
PAY_LOCAL_ONLY
PAY_AMOUNT_MISMATCH
PAY_STATUS_MISMATCH
PAY_SERIAL_MISMATCH
PAY_BIZ_NO_MISMATCH
PAY_CHANNEL_DUPLICATE
PAY_LOCAL_DUPLICATE

REFUND_CHANNEL_ONLY
REFUND_LOCAL_ONLY
REFUND_AMOUNT_MISMATCH
REFUND_STATUS_MISMATCH
REFUND_CHANNEL_DUPLICATE
```

批次汇总同时核对支付笔数/金额、退款笔数/金额与净交易额：

```text
渠道净交易额 = 渠道支付金额 - 渠道退款/冲减金额
平台净交易额 = PaymentOrder.SUCCESS.paidAmount - RefundOrder.SUCCESS.refundAmount
净差额       = 渠道净交易额 - 平台净交易额
```

只有“支付笔数/金额一致 + 退款笔数/金额一致 + 净额一致 + 无 OPEN 差异”才判定 `balanced=true`，避免多笔错误金额互相抵消后被误判平账。汇总接口：`GET /api/reconciliation/imports/{importNo}/summary`。

差异列表支持“核验下钻”：`GET /api/reconciliation/discrepancies/{id}/drilldown` 同时返回渠道账 XLSX 原始行快照、平台 `PaymentOrder/RefundOrder` 候选账本记录，以及业务单号、渠道流水号、金额、状态四个维度的逐项核验结果。普通账单流水接口不返回 `raw_line`，原始行仅在下钻接口中按需读取，避免大账单列表响应膨胀。

当前为**交易账账核对**，不把渠道手续费混入净交易额；手续费应在后续资金/结算账单核对中单独处理。幂等防线包括文件 SHA-256、账单日/种类互斥、Redisson 锁、数据库唯一约束、批次状态校验。

## 6. 技术栈

| 分类 | 技术 | 版本/说明 |
|---|---|---|
| Java | Java | 21 |
| 后端 | Spring Boot | 3.5.16 |
| 服务注册/配置 | Spring Cloud Alibaba Nacos | Spring Cloud Alibaba 2025.0.0.0（nacos-client 3.0.3） |
| 服务调用 | Spring Cloud OpenFeign + Spring Cloud LoadBalancer | Spring Cloud 2025.0.0 |
| ORM | MyBatis-Plus | 3.5.18 |
| 数据库 | 达梦 DM8 / DmJdbcDriver18 | 8.1.2.192 |
| Redis | Spring Data Redis + Redisson | Redisson 3.52.0 |
| MQ | RabbitMQ + Spring AMQP | Spring Boot Starter AMQP |
| 对象存储 | MinIO | 8.5.7，可选 |
| 微信 V3 | wechatpay-apache-httpclient | 0.3.0 |
| 微信 V2 | wxpay-sdk | 0.0.3 |
| 支付宝 | alipay-sdk-java | 4.22.57.ALL |
| API 文档 | springdoc-openapi（Swagger UI） | 2.10.0 |
| 用户前端 | React + Vite + Ant Design | React 18 / Vite 5 / antd 5 |
| 管理前端 | React + Vite + Ant Design + xlsx + Luckysheet | React 18 |

## 7. 项目结构

```text
mall/
├── backend/                          # Maven 聚合父工程
│   ├── mall-common/                  # 共享 Web 响应、异常、认证上下文与基础配置
│   ├── mall-api-order/               # 订单契约、状态枚举与跨进程订单模型
│   ├── mall-api-payment/             # 支付应用契约、DTO/VO、支付事件拓扑与跨进程支付模型
│   ├── mall-api-user/                # 用户应用契约、DTO/VO 与跨进程领域模型
│   ├── mall-api-product/             # 商品与库存应用契约、DTO/VO 与跨进程领域模型
│   ├── mall-api-refund/              # 退款账查询契约、退款账模型与退款状态同步 MQ 拓扑
│   ├── mall-infra/                   # 分布式锁、Outbox、RabbitMQ 公共可靠性、Feign 公共配置等基础设施实现（纯 jar 库）
│   ├── mall-core/                    # BFF 远程适配器与共享配置（纯 jar 库）
│   │   ├── src/main/java/cc/ivera/
│   │   │   ├── shared/
│   │   │   ├── product/              # 商品/库存服务 OpenFeign 远程适配，无本地商品表访问
│   │   │   ├── payment/              # 仅支付服务 OpenFeign 远程适配，无支付表访问
│   │   │   ├── user/                 # 用户服务 OpenFeign 远程适配，无本地用户表访问
│   │   │   └── cart/                 # 购物车 OpenFeign 远程适配，无本地购物车表访问
│   │   ├── src/main/resources/
│   │   │   ├── application-core.yml  # 数据库/Redis/RabbitMQ/用户/商品/支付服务远程调用共享配置
│   │   │   └── mapper/
│   ├── mall-bill-service/            # 账单服务（8088）：微信账单上传、账账核对、差异查询与处理
│   ├── mall-payment-service/         # 支付服务（8086）：支付单/成交事实、微信 V2/V3、支付宝、渠道与应用配置
│   ├── mall-refund-service/          # 退款服务（8087）：退款申请/单/明细、渠道退款、状态同步与退款 MQ 消费
│   ├── mall-order-service/           # 订单服务（8085）：订单/明细/物流、下单 Saga、请求幂等、订单关单与支付事件消费
│   ├── mall-product-service/         # 商品与库存服务（8084）：商品、库存四桶、预占、流水、Excel 导入
│   ├── mall-user-service/            # 用户领域服务（8083）：认证、会话、地址、区划、用户管理
│   ├── mall-cart-service/            # 购物车领域服务（8089）：购物车条目持久化与商品快照聚合
│   ├── mall-user-bff/                # 用户端 BFF（8081），承载用户/商品/购物车入口与 Outbox 中继
│   ├── mall-admin-bff/               # 管理端 BFF（8082），不启动 MQ/定时任务
│   ├── mall-gateway/                 # 网关（8080）：路由与 CORS
│   ├── docs/
│   │   ├── DAMENG_DM8_OPERATIONS.md
│   │   └── RABBITMQ_OPERATIONS.md
│   ├── env/
│   │   ├── docker-compose.yml
│   │   ├── scripts/dm8/              # start-dm8.sh / init-dm8-sql.sh
│   │   └── sql/dm8/schema.sql
│   └── nacos/
│       └── config/                   # Nacos 配置源文件（mall-common.yaml + 各进程 yaml），需发布到 Nacos
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

当前脚本创建 25 张业务表：

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
t_payment_deal
t_inventory_reservation
t_inventory_transaction
t_stock_import
t_order_shipment
t_bill_import
t_bill_record
t_bill_reconcile_discrepancy
t_local_message
t_consumer_dedupe
t_region
t_shipping_address
t_cart_item
```

脚本是当前数据库结构的唯一权威版本，重复执行前会处理既有对象，效果接近清库重建。生产环境执行前务必备份数据并评估 DROP 行为。

## 9. 环境要求

建议：

- JDK 21（后端编译与运行；`maven-compiler-plugin` 使用 `release 21`）。
- Maven 3.6+，且 `JAVA_HOME` 指向 JDK 21（否则 `release 21` 编译失败）。
- Node.js 18+。
- Docker / Docker Compose（DM8 容器）。
- Nacos 3.x（服务注册与配置中心，默认 `127.0.0.1:8848`，需同时开放 gRPC 端口 `9848`）。
- Redis 5.0+。
- RabbitMQ 3.7+。
- MinIO：仅当 `stock.import.storage=minio` 时需要。

## 10. 快速启动

### 10.1 启动基础设施（DM8 / Redis / RabbitMQ）与 Nacos

```powershell
cd backend\env
docker compose -f docker-compose.yml up -d
```

四个容器端口：DM8 `5236`、Redis `6379`、RabbitMQ `5672`（管理台 `15672`）、Nacos `8848`（gRPC `9848`、控制台 `8880`）。

> Nacos 3.x 的控制台已拆到独立端口：浏览器访问 `http://localhost:8880/`，默认账号 `nacos / nacos`。
> `8848` 只提供 HTTP API（上下文 `/nacos`）与 gRPC `9848`；直接打开 `http://localhost:8848/nacos/` 只会返回
> “Nacos Console default port is 8080” 的提示文本，这是 3.x 的预期行为，不是启动故障。
> 如需修改控制台映射端口，设置 `NACOS_CONSOLE_HOST_PORT` 后重新执行 `docker compose up -d nacos`。

RabbitMQ 管理台默认账号 `mall / Cpc2026#@Mq`；Redis 密码 `cpc!23#@`；Nacos 默认账号 `nacos / nacos`（与 `application.yml` 中 `NACOS_USERNAME` / `NACOS_PASSWORD` 一致）。

Nacos 镜像使用 `nacos/nacos-server:v3.0.3`，单机模式 + 内嵌 Derby，已开启鉴权。可用 `NACOS_IMAGE`、`NACOS_PORT`、`NACOS_GRPC_PORT`、
`NACOS_AUTH_TOKEN`、`NACOS_AUTH_IDENTITY_KEY`、`NACOS_AUTH_IDENTITY_VALUE` 等环境变量覆盖。服务注册/配置中心默认地址为
`127.0.0.1:8848`，连接信息由 `NACOS_SERVER_ADDR` / `NACOS_USERNAME` / `NACOS_PASSWORD` / `NACOS_NAMESPACE` / `NACOS_GROUP` 控制。

首次启动后把 `backend/nacos/config/` 下的 `mall-common.yaml` 与各进程 `<应用名>.yaml` 按同名 Data ID、`yaml` 格式发布到同一 namespace / group：

```text
backend/nacos/config/mall-common.yaml          # 所有进程共享（内部令牌）
backend/nacos/config/mall-gateway.yaml         # 网关路由、CORS 与 actuator
backend/nacos/config/mall-user-bff.yaml
backend/nacos/config/mall-admin-bff.yaml
backend/nacos/config/mall-user-service.yaml
backend/nacos/config/mall-cart-service.yaml
backend/nacos/config/mall-product-service.yaml
backend/nacos/config/mall-order-service.yaml
backend/nacos/config/mall-payment-service.yaml
backend/nacos/config/mall-refund-service.yaml
backend/nacos/config/mall-bill-service.yaml
```

各进程通过 `spring.config.import: optional:nacos:...` 导入；`optional:` 保证 Nacos 不可达时仍能用本地
`application.yml` 中的同值配置启动。需要完全脱离 Nacos 时设置 `NACOS_ENABLED=false`，并为 Feign 指定
`USER_SERVICE_BASE_URL` / `PRODUCT_SERVICE_BASE_URL` / `ORDER_SERVICE_BASE_URL` / `PAYMENT_SERVICE_BASE_URL` /
`REFUND_SERVICE_BASE_URL`，同时用 `MALL_*_URI`（如 `MALL_USER_BFF_URI=http://127.0.0.1:8081`）覆盖网关的 `lb://` 路由。

容器健康后，执行初始化脚本：

```bash
cd backend
bash env/scripts/dm8/start-dm8.sh
```

脚本会等待 DM8 健康检查通过，并只在 6 张必需业务表缺失时才按文件名顺序执行 `env/sql/dm8/*.sql`
（表齐全时跳过，不触碰已有数据）。等价的手工入口是 `bash env/scripts/dm8/init-dm8-sql.sh`，加 `--if-missing` 即为
`start-dm8.sh` 的调用方式；不加参数则强制破坏性重建。

开发脚本中包含初始化管理员种子账号，生产部署前必须替换默认凭据。

本版本包含退款商品去向与账账核对字段/索引升级。**存量数据库已有业务数据时不要重新执行完整 `schema.sql`（会清表）**。执行前先通过 `USER_TAB_COLUMNS` / `USER_INDEXES` 确认对象不存在，然后一次性补齐：

```sql
ALTER TABLE t_refund_order ADD goods_disposition VARCHAR(20);
COMMENT ON COLUMN t_refund_order.goods_disposition IS '已发货退款商品去向：LOST-商品丢失/无法回收，RECOVERED-商品已全部回收';

ALTER TABLE t_bill_reconcile_discrepancy ADD local_biz_no VARCHAR(50);
ALTER TABLE t_bill_reconcile_discrepancy ADD local_ledger_no VARCHAR(64);
ALTER TABLE t_bill_reconcile_discrepancy ADD local_serial_no VARCHAR(64);

COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_biz_no IS '平台侧业务单号：支付为订单号，退款为退款单号';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_ledger_no IS '平台账本单号：支付为payment_no，退款为refund_no';
COMMENT ON COLUMN t_bill_reconcile_discrepancy.local_serial_no IS '平台记录的渠道流水号：支付为channel_order_no；退款当前可为空';

CREATE INDEX idx_payment_order_channel_status_paid_time
ON t_payment_order(channel, status, paid_time);

CREATE INDEX idx_refund_order_status_success_time
ON t_refund_order(status, success_time);
```

这两个组合索引服务于按账单日扫描平台支付/退款成功账，避免数据量增大后对 `paid_time` / `success_time` 做大范围扫描。已经存在的字段或索引不要重复执行对应 DDL。

全新环境直接执行最新 `schema.sql` 即可。

### 10.2 配置后端

主要配置文件：

```text
backend/mall-core/src/main/resources/application-core.yml         # 两个 BFF 的共享配置与用户/商品/支付/退款远程客户端配置
backend/mall-user-service/src/main/resources/application.yml      # 端口 8083、用户服务自有 DM8/Redis/JWT 配置
backend/mall-product-service/src/main/resources/application.yml   # 端口 8084、商品服务自有 DM8、库存导入存储与内部令牌配置
backend/mall-order-service/src/main/resources/application.yml     # 端口 8085、订单服务自有 DM8/Redis/RabbitMQ、用户/商品/支付远程地址、Saga 与幂等参数
backend/mall-payment-service/src/main/resources/application.yml   # 端口 8086、支付服务自有 DM8/Redis/RabbitMQ、支付渠道与订单服务远程地址配置
backend/mall-refund-service/src/main/resources/application.yml    # 端口 8087、退款服务自有 DM8/Redis/RabbitMQ、订单/支付/商品服务远程地址配置
backend/mall-bill-service/src/main/resources/application.yml      # 端口 8088、账单服务自有 DM8/Redis、用户/支付/退款服务远程地址配置
backend/mall-user-bff/src/main/resources/application.yml          # 端口 8081、应用名
backend/mall-admin-bff/src/main/resources/application.yml         # 端口 8082、应用名、关闭 MQ 监听
```

重点配置：

| 配置 | 说明 |
|---|---|
| `spring.datasource.*` | DM8 连接；支持 `DM_HOST/DM_PORT/DM_SCHEMA/DM_USERNAME/DM_PASSWORD` |
| `spring.data.redis.*` | Redis |
| `spring.cloud.nacos.discovery.*` / `spring.cloud.nacos.config.*` | 服务注册与配置中心连接，默认 `127.0.0.1:8848`；`NACOS_ENABLED=false` 时同时关闭注册与配置导入 |
| `spring.config.import` | `optional:nacos:mall-common.yaml,optional:nacos:<应用名>.yaml`；BFF 额外先导入 `classpath:application-core.yml` |
| `spring.cloud.openfeign.client.config.default.*` | 内部 Feign 调用默认连接/读取超时 3000/10000ms；`mall-product-service` 读超时覆盖为 30000ms |
| `user.service.base-url` / `product.service.base-url` / `payment.service.base-url` / `order.service.base-url` / `refund.service.base-url` | Feign 直连地址覆盖项，对应 `USER_SERVICE_BASE_URL` / `PRODUCT_SERVICE_BASE_URL` / `PAYMENT_SERVICE_BASE_URL` / `ORDER_SERVICE_BASE_URL` / `REFUND_SERVICE_BASE_URL`；默认留空，走 Nacos 服务发现 + LoadBalancer |
| `internal.service.token` | 服务间令牌，由 `INTERNAL_SERVICE_TOKEN` 注入；用户服务、商品服务、订单服务、支付服务、退款服务、账单服务与两个 BFF 必须一致，空值或不匹配均拒绝 `/internal/**` |
| JVM 时区 | 由 `cc.ivera.shared.infrastructure.config.TimeZoneConfig` 在各服务启动时统一设置为 `Asia/Shanghai`，与 DM8 会话时区、账务日切口径一致；不依赖宿主机时区，也无需额外启动参数 |
| `spring.rabbitmq.*` | RabbitMQ、publisher confirm/returns、listener retry/MANUAL ACK |
| `payment.auth.*` | Access/Refresh TTL、JWT Secret |
| `payment.order.expire-minutes` | 未支付订单超时与 Order Close 延迟队列 TTL |
| `payment.order.timeout-scan-ms` | 订单服务 `TimeoutOrderCloseScheduler` DB 兜底扫描周期，默认 60000ms |
| `order.saga.recovery-delay-ms` | 订单服务判定 Saga 卡住的阈值，默认 300000ms |
| `order.saga.recovery-scan-ms` | 订单服务 `OrderSagaRecoveryScheduler` 扫描周期，默认 60000ms |
| `order.request.ttl-hours` | 下单幂等记录 `t_order_request` 保留时长，默认 24 小时 |
| `payment.refund.status-sync-delay-ms` | Refund Sync MQ 延迟时间，默认 60000ms |
| `payment.refund.status-sync-scan-ms` | `RefundStatusSyncScheduler` DB 兜底扫描周期，默认 60000ms |
| `stock.import.storage` | 商品服务 Excel 导入存储：`local` / `minio` |
| `stock.import.dir` | 商品服务本地 Excel 存储目录 |
| `stock.import.minio.*` | 商品服务 MinIO endpoint/凭据/bucket |

生产环境不要直接沿用仓库中的开发凭据、JWT Secret、数据库密码、Redis 密码、MinIO 凭据或支付密钥。

### 10.3 启动用户服务、商品服务、订单服务、支付服务、退款服务、账单服务、BFF 与网关

`backend` 是 Maven 聚合工程，`mall-core`、`mall-api-*`、`mall-infra` 是纯 jar 库，不单独启动；当前有九个可运行进程：

| 进程 | 端口 | 职责 |
|---|---|---|
| `mall-user-service` | 8083 | 用户注册登录、双 Token 会话、地址、区划、用户管理、密码重置；用户表和用户会话唯一写者 |
| `mall-cart-service` | 8089 | 购物车条目增删改查、勾选、已选清理与商品快照聚合；购物车表唯一写者 |
| `mall-product-service` | 8084 | 商品 CRUD、库存四桶、预占/提交/释放/结转已售、退款回补与货损核销、库存流水与 Excel 导入；商品与库存表唯一写者 |
| `mall-order-service` | 8085 | 订单/明细/物流、下单 Saga 与 `Idempotency-Key` 幂等、订单关单与支付事件消费、超时扫描与物流模拟；订单表、`t_order_request`、`t_saga_instance` 唯一写者 |
| `mall-payment-service` | 8086 | 支付单与成交事实、微信 V2/V3、支付宝、支付渠道/应用配置；支付表唯一写者，不消费 MQ、不扫描 Outbox |
| `mall-refund-service` | 8087 | 退款申请/单/明细、渠道退款编排、退款状态同步；退款表唯一写者，消费退款 MQ 并执行退款扫描 |
| `mall-bill-service` | 8088 | 微信交易账单导入、渠道账与平台支付/退款账逐笔核对、差异查询与人工处理；账单表唯一写者，不消费 MQ、不执行定时任务 |
| `mall-user-bff` | 8081 | 用户/商品/购物车等剩余 Controller；仅保留 Outbox 中继，不再承载订单与对账 |
| `mall-admin-bff` | 8082 | 管理侧剩余 Controller；`spring.rabbitmq.listener.simple.auto-startup=false`，不消费 MQ |
| `mall-gateway` | 8080 | 统一入口与 CORS；退款/取消 → refund-service；`/api/admin/order/**` 与订单前台路径 → order-service；`/api/reconciliation/**` → bill-service；`/api/admin/**` → admin-bff；支付回调/配置/收银台 → payment-service；其余 → user-bff |

```powershell
# 先安装多模块依赖，再分别启动九个进程（各开一个终端）
cd backend
mvn -DskipTests install

# 用户服务、商品服务、订单服务、支付服务、退款服务、账单服务与两个 BFF 的终端必须设置同一个随机内部令牌；以下为占位符，不要直接用于生产
$env:INTERNAL_SERVICE_TOKEN='<替换为本地随机令牌>'

# Nacos 服务注册与配置中心（默认 127.0.0.1:8848）；完全脱离 Nacos 时改为 $env:NACOS_ENABLED='false'
$env:NACOS_SERVER_ADDR='127.0.0.1:8848'

# 1) 用户领域服务（8083）
mvn -pl mall-user-service spring-boot:run

# 2) 购物车领域服务（8089）
mvn -pl mall-cart-service spring-boot:run

# 3) 商品与库存服务（8084）
mvn -pl mall-product-service spring-boot:run

# 4) 订单服务（8085）
mvn -pl mall-order-service spring-boot:run

# 5) 支付服务（8086）
mvn -pl mall-payment-service spring-boot:run

# 6) 退款服务（8087）
mvn -pl mall-refund-service spring-boot:run

# 7) 账单服务（8088）
mvn -pl mall-bill-service spring-boot:run

# 8) 用户端 BFF（8081）
mvn -pl mall-user-bff spring-boot:run

# 9) 管理端 BFF（8082）
mvn -pl mall-admin-bff spring-boot:run

# 10) 网关（8080，不需要内部令牌）
mvn -pl mall-gateway spring-boot:run
```

访问：

```text
API:          http://localhost:8080          （经网关按前缀转发）
Swagger:      http://localhost:8080/swagger-ui.html
OpenAPI:      http://localhost:8080/v3/api-docs
用户端 BFF:    http://localhost:8081          （仅供本地排障，前端不要直连）
管理端 BFF:    http://localhost:8082          （仅供本地排障，前端不要直连）
用户领域服务:  http://localhost:8083          （仅供服务间调用与本地排障）
购物车领域服务: http://localhost:8089          （仅供服务间调用与本地排障）
商品库存服务:  http://localhost:8084          （仅供服务间调用与本地排障）
订单服务:      http://localhost:8085          （仅供服务间调用与本地排障）
支付服务:      http://localhost:8086          （仅供服务间调用与本地排障）
退款服务:      http://localhost:8087          （仅供服务间调用与本地排障）
用户服务、购物车服务、商品服务、订单服务、支付服务、退款服务、账单服务与 BFF 端口可用 `SERVER_PORT` 覆盖。默认所有进程向 Nacos 注册并通过服务名互相发现：内部调用由 OpenFeign + LoadBalancer 完成，网关路由使用 `lb://`。需要直连时可显式设置 `USER_SERVICE_BASE_URL`、`CART_SERVICE_BASE_URL`、`PRODUCT_SERVICE_BASE_URL`、`ORDER_SERVICE_BASE_URL`、`PAYMENT_SERVICE_BASE_URL`、`REFUND_SERVICE_BASE_URL`；网关可用 `MALL_USER_BFF_URI`、`MALL_ADMIN_BFF_URI`、`MALL_BILL_SERVICE_URI`、`MALL_ORDER_SERVICE_URI`、`MALL_PAYMENT_SERVICE_URI`、`MALL_REFUND_SERVICE_URI` 覆盖 `lb://` 默认值。`NACOS_ENABLED=false` 时注册与配置导入同时关闭。`/internal/**` 不经过网关。各进程仍同库同 schema，无需执行增量 SQL。
```

用户服务、商品服务、订单服务、支付服务、退款服务、账单服务与 BFF 端口可用 `SERVER_PORT` 覆盖；BFF 用 `USER_SERVICE_BASE_URL`、`PRODUCT_SERVICE_BASE_URL`、`MALL_PAYMENT_SERVICE_URI`、`REFUND_SERVICE_BASE_URL` 分别指定用户、商品、支付与退款服务地址，订单服务用 `USER_SERVICE_BASE_URL`、`PRODUCT_SERVICE_BASE_URL`、`PAYMENT_SERVICE_BASE_URL` 指定用户、商品与支付服务地址，支付/退款服务用 `ORDER_SERVICE_BASE_URL` 指定订单服务地址，账单服务用 `USER_SERVICE_BASE_URL`、`PAYMENT_SERVICE_BASE_URL`、`REFUND_SERVICE_BASE_URL` 指定用户、支付与退款服务地址。网关分别用 `MALL_USER_BFF_URI`（默认 `http://127.0.0.1:8081`）、`MALL_ADMIN_BFF_URI`（默认 `http://127.0.0.1:8082`）、`MALL_BILL_SERVICE_URI`（默认 `http://127.0.0.1:8088`）、`MALL_ORDER_SERVICE_URI`（默认 `http://127.0.0.1:8085`）、`MALL_PAYMENT_SERVICE_URI`（默认 `http://127.0.0.1:8086`）、`MALL_REFUND_SERVICE_URI`（默认 `http://127.0.0.1:8087`）指定后端地址。`/internal/**` 不经过网关。各进程仍同库同 schema，无需执行增量 SQL。

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

后端当前不包含自动化测试代码（所有模块的 `src/test` 已移除），变更验证以编译通过为准：

```powershell
cd backend
mvn clean test
```

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

后端当前有 28 个对外 Controller（user-bff 5、admin-bff 6、order-service 4、payment-service 6、refund-service 7）与 6 个内部契约 Controller，主要前缀如下：

| 前缀 | 归属 | 功能 |
|---|---|---|
| `/api/auth` | user | 登录、注册、刷新、退出、密码 |
| `/api/product` | user | 商城商品 |
| `/api/cart` | user | 购物车 |
| `/api/checkout` | order | 结算、下单（需 `Idempotency-Key`）、支付入口 |
| `/api/order` / `/api/order-info` | order | 订单、履约、查询 |
| `/api/refund-applies` / `/api/refund-info` | refund | 退款申请/退款查询 |
| `/api/wx-pay/refunds/notify` / `/api/wx-pay/query-refund/**` | refund | 退款通知与退款查询 |
| `/api/ali-pay/trade/fastpay/refund/**` | refund | 支付宝退款查询 |
| `/api/order/{orderNo}/cancel` | refund | 已支付订单取消/退款编排 |
| `/api/wx-pay` | payment | 微信支付 V3 |
| `/api/wx-pay-v2` | payment | 微信支付 V2 |
| `/api/ali-pay` | payment | 支付宝 |
| `/api/payment-app` | payment | 支付应用 |
| `/api/payment-channel` | payment | 支付渠道配置 |
| `/api/payment-config` | payment | 支付配置缓存 |
| `/api/regions` | user | 行政区划 |
| `/api/user/address` | user | 用户地址 |
| `/api/admin/order` | order | 管理端订单/发货/线下收款 |
| `/api/admin/products` | admin | 管理端商品库存 |
| `/api/admin/stock` | admin | 库存流水/Excel 导入 |
| `/api/admin/refund` | refund | 管理端退款 |
| `/api/admin/users` | admin | 用户管理 |
| `/api/admin/users/{userId}/sessions` | admin | 在线会话查询/按设备下线 |
| `/api/admin/password-reset-requests` | admin | 密码重置申请 |
| `/api/reconciliation` | bill | 对账 |

非用户非商品领域实现仍由两个 BFF 共用 `mall-core`；用户领域与购物车实现独立在 `mall-user-service`，商品与库存领域实现独立在 `mall-product-service`，订单与物流实现独立在 `mall-order-service`，支付实现独立在 `mall-payment-service`，退款实现独立在 `mall-refund-service`，账单核对实现独立在 `mall-bill-service`。BFF/订单通过 `mall-core` 中的远程适配器调用，退款侧通过 `mall-api-order` / `mall-api-payment` / `mall-api-product` 契约远程访问，账单侧通过 `mall-api-payment` / `mall-api-refund` 契约读取平台账本；公开 `/api/**` 路由不变。退款通知、退款查询与订单取消经网关直连退款服务，其余支付路由直连支付服务。BFF 不持有独立写权限。

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
消息可靠性：Outbox + publisher confirm + MANUAL ACK + Retry + Failure DLQ
最终一致性：DB Scheduler 独立兜底
```

修改核心交易代码前，请先阅读 [AGENTS.md](AGENTS.md) 和 [CODE_INTRO.md](CODE_INTRO.md)。
