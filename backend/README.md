# backend — 电商商城平台后端服务

Spring Boot 2.3.7（Java 8，包名 `cc.ivera`）单体后端，为 [user-ui](../user-ui)（用户商城）与 [admin-ui](../admin-ui)（管理后台）提供
REST API，默认端口 `8080`。

## 功能概览

- **交易链路**：购物车（Redis）→ 下单（库存预占 + CAS 防超卖）→ 支付（微信 Native V2/V3、支付宝扫码）→ 延迟关单（MQ + 兜底扫描）→
  发货/物流 → 确认收货（结转已售）
- **退款**：6 种类型分项退款，受理即冻结额度防超退，退货签收回补，仅退款核销货损，重复/晚到支付自动冲正，线下收款单退款不调渠道
- **对账**：微信交易账单 CSV 上传式对账，8 类差异识别，全链路幂等
- **工程保障**：并发控制三层（Redis 通知幂等 → Redisson 分布式锁 → CAS 条件更新；**不使用 `select ... for update`**）、双 Token
  认证、本地消息表（Outbox）、RabbitMQ 消费重试与 DB 兜底任务

完整架构、上下文边界、实体模型、路由与服务清单见 [../CODE_INTRO.md](../CODE_INTRO.md)。

## 技术栈

| 分类     | 技术                                                           | 版本            |
|--------|--------------------------------------------------------------|---------------|
| 框架     | Spring Boot                                                  | 2.3.7.RELEASE |
| 语言     | Java                                                         | 1.8           |
| ORM    | MyBatis-Plus                                                 | 3.3.1         |
| 数据库    | 达梦 DM8（DmJdbcDriver18）                                       | 8.1.2.192     |
| 缓存/锁   | Redis + Redisson                                             | 5.0+ / 3.16.8 |
| 消息队列   | RabbitMQ（Spring AMQP）                                        | 3.7+          |
| 对象存储   | MinIO（可选，Excel 存储）                                           | 8.5.7         |
| 支付 SDK | WechatPay APIv3 0.3.0 / wxpay-sdk 0.0.3 / Alipay SDK 4.22.57 | —             |
| API 文档 | Swagger                                                      | 2.7.0         |

## 目录结构

后端按 DDD 限界上下文划分包，每个上下文内部统一四层：

```
backend/src/main/java/cc/ivera/
├── Application.java           # 启动类（@EnableRabbit / @EnableScheduling）
├── shared/                    # 共享内核：Money/异常/锁模板/本地消息、security、web、MyBatis/Redisson/Swagger 配置
├── product/                   # 商品与库存：商品 CRUD、库存四桶、预占、Excel 导入与文件存储
├── order/                     # 订单与履约：下单、关单、发货、模拟物流、确认收货
├── payment/                   # 支付：微信 V2/V3、支付宝、支付单/支付记录、渠道配置
├── refund/                    # 退款：退款单/明细、额度策略 RefundPolicy、渠道退款、状态同步
├── user/                      # 用户/认证/收货地址/行政区划/密码重置
├── cart/                      # 购物车（Redis 仓储，infrastructure.redis）
└── bill/                      # 微信账单上传对账（CSV 解析、8 类差异）

# 每个上下文内部四层：
#   interfaces/       Controller、DTO、VO、MQ 消费者、定时任务
#   application/      应用服务接口 + impl（用例编排、事务边界），微信门面实现在 impl/wxpay/
#   domain/           纯 POJO 领域模型、枚举、策略、repository/gateway 端口（不依赖 Spring/MP）
#   infrastructure/   persistence 子包内为 po / mapper / converter / repository；另有网关实现、MQ 拓扑、存储适配、配置

backend/src/main/resources/
├── mapper/                    # MyBatis XML（18 个，平铺；namespace 指向各上下文 persistence.mapper）
└── application.yml            # 连接参数与业务配置
backend/src/test/              # 单元/特征测试（16 个测试类、121 个用例，纯 JUnit5 + Mockito）
backend/docs/                  # DM8 与 RabbitMQ 运维手册
backend/env/
├── docker-compose.dm8.yml     # DM8 容器（端口 5236，健康检查 + dm8-data 卷）
└── sql/dm8/schema.sql         # 一体化全量建表（22 张）+ 种子数据，可重复执行，含遗留表 DROP 守卫
```

## 快速启动

### 1. 启动 DM8 并初始化

```powershell
cd env
docker compose -f docker-compose.dm8.yml up -d
```

容器 healthy 后，用 DM 管理工具连接 `localhost:5236`（默认 `SYSDBA / Cpc2026#@Dm`，schema `SYSDBA`），执行
`env/sql/dm8/schema.sql`（可重复执行，等同清库重建）。详见 [docs/DAMENG_DM8_OPERATIONS.md](docs/DAMENG_DM8_OPERATIONS.md)。

### 2. 配置连接

修改 `src/main/resources/application.yml`（默认指向部署环境 `192.168.1.200`）：

- **DM8**：`DM_HOST` / `DM_PORT` / `DM_SCHEMA` / `DM_USERNAME` / `DM_PASSWORD` 环境变量覆盖
- **Redis / RabbitMQ**：`spring.redis.*`、`spring.rabbitmq.*`
- **JWT 密钥**：生产必须用 `AUTH_JWT_SECRET` 覆盖默认值
- **Excel 存储**：`stock.import.storage` 设为 `local`（配合 `stock.import.dir`）或 `minio`（`STOCK_IMPORT_MINIO_*` 环境变量覆盖）
- **支付商户参数**（微信私钥、支付宝密钥等）不在配置文件中，初始化后在管理后台「支付配置」维护（存于 `t_payment_channel`）

### 3. 启动

```powershell
mvn spring-boot:run
```

- 应用地址：http://localhost:8080
- Swagger 文档：http://localhost:8080/swagger-ui.html

后端须在 DM8 容器健康检查通过后再启动。

## 测试

`src/test` 下有 16 个测试类、121 个用例，纯 JUnit 5 + Mockito（**不连真实 DM8/Redis/RabbitMQ/支付渠道**），覆盖领域聚合状态机、
PO Converter 全字段往返、`RefundPolicy` 额度核算、`WxTradeBillParser` 账单解析、登录防护与支付/退款/购物车应用服务：

```powershell
mvn test
```

按 [../AGENTS.md](../AGENTS.md) 规则：新行为或缺陷修复必须补针对性测试；重构既有行为前先补特征测试锁定现状，跑全量回归。

## 运维文档

- [docs/DAMENG_DM8_OPERATIONS.md](docs/DAMENG_DM8_OPERATIONS.md) — DM8 启动/初始化/清库重建
- [docs/RABBITMQ_OPERATIONS.md](docs/RABBITMQ_OPERATIONS.md) — 队列拓扑、可靠性与冒烟测试清单
