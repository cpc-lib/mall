# backend — 电商商城平台后端服务

Spring Boot 3.5.16（Java 21，包名 `cc.ivera`）后端，采用 Maven 聚合：`mall-core` 是非用户非商品领域内核与远程适配器（纯 jar 库），
[user-service](mall-user-service)（8083）独立承载用户领域实现，[cart-service](mall-cart-service)（8089）独立承载购物车领域实现，[product-service](mall-product-service)（8084）独立承载商品与库存领域实现，
[user-bff](mall-user-bff)（8081）与 [admin-bff](mall-admin-bff)（8082）只承载各自侧 Controller，对外入口由
[mall-gateway](mall-gateway) 占用 `8080`，两个前端 baseURL 保持不变。
非用户非商品领域实现由两个 BFF 共用；用户表与 Redis 用户会话只由用户服务写入，购物车表（`t_cart_item`）只由购物车服务写入，商品/库存表只由商品服务写入，BFF 不持有独立写权限。

## 功能概览

- **交易链路**：购物车（DM8 `t_cart_item`）→ 下单（库存预占 + CAS 防超卖）→ 支付（微信 Native V2/V3、支付宝扫码）→ 延迟关单（MQ + 兜底扫描）→
  发货/物流 → 确认收货（结转已售）
- **退款**：6 种类型分项退款，受理即冻结额度防超退，退货签收回补，仅退款核销货损，重复/晚到支付自动冲正，线下收款单退款不调渠道
- **对账**：微信交易账单 CSV 上传式对账，8 类差异识别，全链路幂等
- **工程保障**：并发控制三层（Redis 通知幂等 → Redisson 分布式锁 → CAS 条件更新；**不使用 `select ... for update`**）、双 Token
  认证、本地消息表（Outbox）、RabbitMQ 消费重试与 DB 兜底任务

完整架构、上下文边界、实体模型、路由与服务清单见 [../CODE_INTRO.md](../CODE_INTRO.md)。

## 技术栈

| 分类     | 技术                                                           | 版本            |
|--------|--------------------------------------------------------------|---------------|
| 框架     | Spring Boot                                                  | 3.5.16        |
| 语言     | Java                                                         | 21            |
| ORM    | MyBatis-Plus                                                 | 3.5.18        |
| 数据库    | 达梦 DM8（DmJdbcDriver18）                                       | 8.1.2.192     |
| 缓存/锁   | Redis + Redisson                                             | 5.0+ / 3.52.0 |
| 消息队列   | RabbitMQ（Spring AMQP）                                        | 3.7+          |
| 对象存储   | MinIO（可选，Excel 存储）                                           | 8.5.7         |
| 支付 SDK | WechatPay APIv3 0.3.0 / wxpay-sdk 0.0.3 / Alipay SDK 4.22.57 | —             |
| API 文档 | springdoc-openapi（Swagger UI）                                 | 2.10.0        |

## 目录结构

后端按 DDD 限界上下文划分包，每个上下文内部统一四层。`mall-core` 只保留两个 BFF 使用的远程适配器与共享配置，
用户领域实现位于 `mall-user-service`，购物车领域实现位于 `mall-cart-service`，商品与库存领域实现位于 `mall-product-service`，订单、支付、退款与账单领域
分别独立在各自服务中；对外 Controller 归所属 BFF 或领域服务，保留原包名 `cc.ivera.<context>.interfaces`：

```
backend/                            # Maven 聚合父工程（packaging=pom）
├── mall-common/                    # 共享 Web 响应、异常、认证上下文与基础配置
├── mall-api-user/                  # 用户应用服务接口、DTO/VO、跨进程领域模型
├── mall-api-product/               # 商品/库存应用服务接口、DTO/VO、跨进程领域模型
├── mall-api-order/                 # 订单/退款额度/物流应用服务接口与跨进程领域模型
├── mall-api-payment/               # 支付应用服务接口、内部 DTO、支付事件与跨进程领域模型
├── mall-api-refund/                # 退款应用服务接口、内部 DTO 与跨进程领域模型
├── mall-infra/                     # 分布式锁、Outbox、MQ 公共实现、Feign 公共配置（jar 库）
├── mall-core/src/main/java/cc/ivera/
│   ├── shared/                     # 共享配置：Redisson/OpenAPI、内部服务鉴权、基础异常与 Money
│   ├── product/                    # 仅商品服务 OpenFeign 远程适配器，无本地商品表访问
│   ├── payment/                    # 仅支付服务 OpenFeign 远程适配器，无支付表访问
│   ├── user/                       # 仅用户服务 OpenFeign 远程适配器，无本地用户表访问
│   └── cart/                       # 仅购物车 OpenFeign 远程适配，无本地购物车表访问
├── mall-core/src/main/resources/
│   ├── mapper/                     # 已无领域 Mapper；保留目录以维持资源契约
│   └── application-core.yml        # 两个 BFF 的连接参数、业务配置与用户/商品/支付远程客户端配置
├── mall-product-service/           # 商品/库存领域服务（8084）：商品、库存四桶、预占、流水、Excel 导入
├── mall-user-service/              # 用户领域服务（8083）：认证、会话、地址、区划、用户管理、密码重置
├── mall-cart-service/              # 购物车领域服务（8089）：购物车条目持久化与商品快照聚合
├── mall-order-service/             # 订单领域服务（8085）：订单/物流、下单 Saga、幂等、关单与支付事件消费
├── mall-payment-service/           # 支付领域服务（8086）：支付单/成交、微信/支付宝、渠道与应用配置
├── mall-refund-service/            # 退款领域服务（8087）：退款单/明细、渠道退款、状态同步
├── mall-bill-service/              # 账单领域服务（8088）：微信交易账单导入、账账核对、差异处理
├── mall-user-bff/                  # 用户端 BFF（8081）：用户侧 Controller；唯一启用 Outbox 中继
├── mall-admin-bff/                 # 管理端 BFF（8082）：管理侧 Controller；关闭 MQ 监听，不跑定时任务
├── mall-gateway/                   # 网关（8080）：路由与 CORS
├── docs/                           # DM8 与 RabbitMQ 运维手册
└── env/
    ├── docker-compose.yml          # 基础设施容器：DM8(5236) / Redis(6379) / RabbitMQ(5672+15672) / Nacos(8848+9848+8880，控制台 http://localhost:8880/)
    └── sql/dm8/schema.sql          # 一体化全量建表 + 种子数据，可重复执行，含遗留表 DROP 守卫

# 每个上下文内部四层：
#   interfaces/       对外/内部 Controller、MQ 消费者、定时任务
#   application/      应用服务接口 + impl（用例编排、事务边界），微信门面实现在 impl/wxpay/
#   domain/           纯 POJO 领域模型、枚举、策略、repository/gateway 端口（不依赖 Spring/MP）
#   infrastructure/   persistence 子包内为 po / mapper / converter / repository；另有网关实现、MQ 拓扑、存储适配、配置
```

## 快速启动

### 1. 启动基础设施并初始化

```powershell
cd env
docker compose -f docker-compose.yml up -d
```

容器 healthy 后，用 DM 管理工具连接 `localhost:5236`（默认 `SYSDBA / Cpc2026#@Dm`，schema `SYSDBA`），执行
`env/sql/dm8/schema.sql`（可重复执行，等同清库重建）。详见 [docs/DAMENG_DM8_OPERATIONS.md](docs/DAMENG_DM8_OPERATIONS.md)。

### 2. 配置连接

两个 BFF 的共享配置位于 `mall-core/src/main/resources/application-core.yml`；用户服务与商品服务分别使用自己的
`mall-user-service/src/main/resources/application.yml`、`mall-product-service/src/main/resources/application.yml`，不导入共享配置。
DM8 默认指向本机 `127.0.0.1:5236`，可用 `DM_HOST` 覆盖为部署环境地址：

- **DM8**：`DM_HOST` / `DM_PORT` / `DM_SCHEMA` / `DM_USERNAME` / `DM_PASSWORD` 环境变量覆盖
- **Redis / RabbitMQ**：`spring.data.redis.*`、`spring.rabbitmq.*`
- **Nacos**：所有进程默认注册到 `NACOS_SERVER_ADDR`（默认 `127.0.0.1:8848`）并导入 `mall-common.yaml` 与 `<应用名>.yaml`；`NACOS_ENABLED=false` 可关闭注册与配置导入
- **用户服务调用**：OpenFeign 默认按服务名 `mall-user-service` 走 Nacos 发现；可用 `USER_SERVICE_BASE_URL` 指定直连地址
- **商品服务调用**：OpenFeign 默认按服务名 `mall-product-service`；可用 `PRODUCT_SERVICE_BASE_URL` 指定直连地址
- **内部认证**：用户服务、商品服务与两个 BFF 必须设置同一个 `INTERNAL_SERVICE_TOKEN`；空值或不匹配均拒绝 `/internal/**`，网关不转发该前缀
- **JWT 密钥**：生产必须用 `AUTH_JWT_SECRET` 覆盖默认值
- **Excel 存储**：`stock.import.storage` 设为 `local`（配合 `stock.import.dir`）或 `minio`（`STOCK_IMPORT_MINIO_*` 环境变量覆盖）
- **支付商户参数**（微信私钥、支付宝密钥等）不在配置文件中，初始化后在管理后台「支付配置」维护（存于 `t_payment_channel`）

### 3. 启动

```powershell
# 先安装多模块依赖，再分别启动九个进程（各开一个终端）
mvn -DskipTests install

# 服务间调用与两个 BFF 的终端必须设置同一个随机内部令牌；以下为占位符，不要直接用于生产
$env:INTERNAL_SERVICE_TOKEN='<替换为本地随机令牌>'
# Nacos 服务注册与配置中心，默认 127.0.0.1:8848
$env:NACOS_SERVER_ADDR='127.0.0.1:8848'

mvn -pl mall-user-service spring-boot:run    # 8083：用户领域服务，不启动 MQ/定时任务
mvn -pl mall-cart-service spring-boot:run    # 8089：购物车领域服务，不启动 MQ/定时任务
mvn -pl mall-product-service spring-boot:run # 8084：商品/库存领域服务，不启动 MQ/定时任务
mvn -pl mall-order-service spring-boot:run   # 8085：订单/物流领域服务 + 关单与支付事件消费 + 定时兜底
mvn -pl mall-payment-service spring-boot:run # 8086：支付领域服务，不启动 MQ/定时任务
mvn -pl mall-refund-service spring-boot:run  # 8087：退款领域服务 + 退款消费与状态同步扫描
mvn -pl mall-bill-service spring-boot:run    # 8088：账单核对领域服务，不启动 MQ/定时任务
mvn -pl mall-user-bff spring-boot:run        # 8081：用户侧入口 + Outbox 中继
mvn -pl mall-admin-bff spring-boot:run       # 8082：管理侧入口，不启动 MQ/定时任务
mvn -pl mall-gateway spring-boot:run         # 8080：统一入口与 CORS，不需要内部令牌
```

- 用户领域服务：http://localhost:8083（用户；服务间调用与本地排障用；不经过网关）
- 购物车领域服务：http://localhost:8089（购物车；服务间调用与本地排障用；不经过网关）
- 商品库存服务：http://localhost:8084（服务间调用与本地排障用；不经过网关）
- 订单物流服务：http://localhost:8085（服务间调用与本地排障用；不经过网关）
- 支付服务：http://localhost:8086（服务间调用与支付回调分流用；不经过网关）
- 退款服务：http://localhost:8087（服务间调用与退款回调分流用；不经过网关）
- 账单服务：http://localhost:8088（服务间调用与本地排障用；不经过网关）
- 用户端 BFF：http://localhost:8081（本地排障用；前端请经网关访问）
- 管理端 BFF：http://localhost:8082（本地排障用；前端请经网关访问）
- 经网关访问：Swagger UI http://localhost:8080/swagger-ui.html，OpenAPI JSON http://localhost:8080/v3/api-docs
- 网关分流：退款/关单 → refund-service；`/api/admin/order/**` → order-service；`/api/reconciliation/**` → bill-service；`/api/admin/**` → admin-bff；`/api/wx-pay/**`、`/api/wx-pay-v2/**`、`/api/ali-pay/**`、`/api/payment-*/**` → payment-service；`/api/checkout/**`、`/api/order-info/**`、`/api/order/**` → order-service；其余 `/api/**` → user-bff

用户服务、商品服务、订单服务、支付服务、退款服务、账单服务与 BFF 端口可用 `SERVER_PORT` 覆盖；网关路由默认使用 `lb://mall-xxx-service`，可用 `MALL_USER_BFF_URI`（默认 `lb://mall-user-bff`）、`MALL_ADMIN_BFF_URI`（默认 `lb://mall-admin-bff`）等 `MALL_*_URI` 覆盖为直连地址；Feign 用 `USER_SERVICE_BASE_URL`、`CART_SERVICE_BASE_URL`、`PRODUCT_SERVICE_BASE_URL`、`ORDER_SERVICE_BASE_URL`、`PAYMENT_SERVICE_BASE_URL`、`REFUND_SERVICE_BASE_URL` 指定直连地址，默认留空走服务发现。

后端须在 DM8 容器健康检查通过后再启动。`mall-core` 是库，不单独启动；后台任务按领域归属执行：订单关单、支付事件消费、超时扫描、物流模拟与下单 Saga 恢复在 `mall-order-service`，退款冲正、退款状态同步与其扫描在 `mall-refund-service`，Outbox 中继只在 `mall-user-bff`；`mall-admin-bff`、`mall-payment-service`、`mall-bill-service` 不开启 MQ 与 Scheduling。各进程同库同 schema，无需增量 SQL。

## 测试

后端当前不包含自动化测试代码（所有模块的 `src/test` 已移除），变更验证以编译通过为准：

```powershell
$env:JAVA_HOME='D:\develop\java\jdk21.0.11_10'   # Maven 必须运行在 JDK 21 上
mvn clean test
```


## 运维文档

- [docs/DAMENG_DM8_OPERATIONS.md](docs/DAMENG_DM8_OPERATIONS.md) — DM8 启动/初始化/清库重建
- [docs/RABBITMQ_OPERATIONS.md](docs/RABBITMQ_OPERATIONS.md) — 队列拓扑、可靠性与冒烟测试清单
