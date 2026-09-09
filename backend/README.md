# backend — 电商商城平台后端服务

Spring Boot 2.3.7（Java 8，包名 `cc.ivera`）单体后端，为 [user-ui](../user-ui)（用户商城）与 [admin-ui](../admin-ui)（管理后台）提供
REST API，默认端口 `8080`。

## 功能概览

- **交易链路**：购物车（Redis）→ 下单（库存预占 + CAS 防超卖）→ 支付（微信 Native V2/V3、支付宝扫码）→ 延迟关单（MQ + 兜底扫描）→
  发货/物流 → 确认收货（结转已售）
- **退款**：6 种类型分项退款，三层额度冻结防超退，退货签收回补，重复/晚到支付自动冲正
- **对账**：微信交易账单 CSV 上传式对账，8 类差异识别，全链路幂等
- **工程保障**：三层并发控制（Redis 通知幂等 → Redisson 分布式锁 → DB 行锁 + CAS）、双 Token 认证、本地消息表（Outbox）、RabbitMQ
  消费重试与兜底任务

完整架构、实体模型、路由与服务清单见 [../CODE_INTRO.md](../CODE_INTRO.md)。

## 技术栈

| 分类     | 技术                                                           | 版本            |
|--------|--------------------------------------------------------------|---------------|
| 框架     | Spring Boot                                                  | 2.3.7.RELEASE |
| ORM    | MyBatis-Plus                                                 | 3.3.1         |
| 数据库    | 达梦 DM8（DmJdbcDriver18）                                       | 8.1.2.192     |
| 缓存/锁   | Redis + Redisson                                             | 5.0+ / 3.16.8 |
| 消息队列   | RabbitMQ（Spring AMQP）                                        | 3.7+          |
| 对象存储   | MinIO（可选，Excel 存储）                                           | 8.5.7         |
| 支付 SDK | WechatPay APIv3 0.3.0 / wxpay-sdk 0.0.3 / Alipay SDK 4.22.57 | —             |

## 目录结构

```
backend/
├── src/main/java/cc/ivera/
│   ├── controller/            # REST 控制器（商城 /api/* + 管理 /api/admin/*）
│   ├── service/               # 业务服务（交易/退款/库存/对账/wxpay 门面/物流）
│   ├── entity/  enums/        # 表实体与状态机枚举
│   ├── mapper/                # MyBatis Mapper 接口
│   ├── security/              # 认证拦截器、JWT、登录防护
│   ├── mq/  job/  lock/       # MQ 消费者、定时任务、分布式锁
│   └── config/                # Redisson/MQ 拓扑/支付配置加载/Swagger
├── src/main/resources/
│   ├── mapper/                # MyBatis XML
│   └── application.yml        # 连接参数与业务配置
├── docs/                      # DM8 与 RabbitMQ 运维手册
└── env/
    ├── docker-compose.dm8.yml # DM8 容器（端口 5236）
    └── sql/dm8/schema.sql     # 一体化全量建表 + 种子数据
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
- **支付商户参数**（微信私钥、支付宝密钥等）不在配置文件中，初始化后在管理后台「支付配置」维护（存于 `t_payment_channel`）

### 3. 启动

```powershell
mvn spring-boot:run
```

- 应用地址：http://localhost:8080
- Swagger 文档：http://localhost:8080/swagger-ui.html

## 测试

当前无自动化测试套件（`src/test` 为空）。按 [../AGENTS.md](../AGENTS.md) 规则，重构遗留行为前需先补特征测试，之后用
`mvn test` 回归。

## 运维文档

- [docs/DAMENG_DM8_OPERATIONS.md](docs/DAMENG_DM8_OPERATIONS.md) — DM8 启动/初始化/清库重建
- [docs/RABBITMQ_OPERATIONS.md](docs/RABBITMQ_OPERATIONS.md) — 队列拓扑、可靠性与冒烟测试清单
