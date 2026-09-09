# 电商商城平台

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-1.8-orange.svg)](https://www.oracle.com/java/technologies/javase-jdk8-downloads.html)
[![DM8](https://img.shields.io/badge/DM8-达梦数据库-blue.svg)]()

## 项目简介

基于 Spring Boot 的电商商城平台：集成**微信支付 V2/V3** 与**支付宝**通道，覆盖「浏览商品 → 购物车 → 下单 → 支付 →
发货/物流 → 确认收货 → 退款/退货 → 账单对账」完整交易闭环，并实现企业级并发控制、幂等保障、库存四桶预占与货损核销、分项退款与上传式账单对账。

前端拆分为两个独立工程，与后端 REST 契约对齐：

- **user-ui** — 用户商城（React 18，移动 App 风格界面，含收货地址、购物车、订单、退款申请、用户中心）
- **admin-ui** — 管理后台（React 18，左侧导航 + 列表 + 抽屉详情，含订单/发货/库存/退款/对账/支付配置管理）

## 核心特性

**交易链路**

- **微信支付 V3**：扫码支付（Native 二维码轮询）、退款、订单查询、账单下载；**微信支付 V2**：扫码支付与通知
- **支付宝**：扫码支付（表单跳转）、退款、订单查询、账单下载
- **多商品订单**：主子表结构，明细快照价；支付配置（渠道/应用）存数据库动态加载，订单绑定支付应用
- **Redis 购物车**：实时读取商品最新价格与库存；管理员角色禁止访问购物车/下单接口
- **收货地址**：三级行政区划级联 + 详细地址，结算时选择收货地址
- **库存四桶模型**：`available / locked / sold / lost` 四桶恒等于入库总量。下单 `LOCKED` 预占（可用转锁定，CAS 防超卖，不足则整单回滚）→
  支付成功 `COMMITTED`（仅推进状态，库存保持锁定）→ 确认收货结转已售（locked→sold）；关单/取消 `RELEASED` 归还可用；全部本地事务同步执行
- **订单履约**：待发货 → 模拟发货（运单号）→ 定时模拟物流推进（已发货 → 运输中 → 已送达）→ **送达后用户方可确认收货**
  （订单列表透出物流状态并展示「运输中/已送达」标签）；已付款未发货取消自动生成退款申请
- **分项退款**：6 种退款类型（未发货取消/退货退款/仅退款/差价退款/重复支付/晚到支付自动冲正），待审核可编辑/撤销，受理后冻结额度动态防超退，最后一件吃尾差；
  **仅退款支持一键整单全额退**（免手填数量，服务端按整单剩余可退自动生成明细）
- **货损核销**：仅退款不退货时货物不回仓——已发货未收货 `locked→lost`、已收货 `sold→lost`，以 `biz_no`（退款单号+明细）幂等核销并写库存流水
- **主动查单**：用户端与管理端均可主动向渠道查询订单支付状态并同步

**管理后台**

- **订单管理**：多状态线筛选、详情抽屉、强制关单（幂等）、标记已付、渠道查单、支付单渠道尝试记录
- **订单发货**：待发货列表、模拟发货
- **商品库存**：新增商品、库存调整（单个/批量）、上架/下架、库存流水（四桶 delta）服务端分页查询，货损列高亮
- **批量库存维护**：Excel（.xlsx）导入 → 生成导入记录 → 全屏 Luckysheet 编辑器核对/编辑 → 确认入库（CAS 防重复执行）；失败行记录原因
- **Excel 文件存储**：`stock.import.storage=local|minio` 可切换，本地磁盘或 MinIO 对象存储；记录级路由，历史文件按原后端读取
- **退款受理**：受理/拒绝/退货签收/渠道重试/状态查询/差价退款
- **用户管理**：分页搜索、禁用启用、用户详情、登录锁定（3 次失败锁 10 分钟，仅锁定输错密码的用户名，不按网络/IP 锁定）；管理员顶栏自助修改密码（改密后全部
  Token 失效强制重登录）
- **密码重置**：用户提交找回申请 → 管理员受理生成随机密码（仅展示一次）或直接按用户重置
- **账单对账**：上传微信交易账单 CSV（ALL/SUCCESS/REFUND），自动解析入库并对账，8 类差异识别，人工标记处理，全链路幂等
- **下载账单**：跳转微信/支付宝官方账单下载接口

**工程保障**

- **三层并发控制**：通知幂等检查（Redis）→ Redisson 分布式锁 → 数据库行锁 + CAS 状态更新
- **双 Token 认证**：Access 30 分钟 + Refresh 7 天，401 单飞（single-flight）无感刷新
- **本地消息表（Outbox）**：关单/退款同步消息先落库再投递，broker 确认后标记，失败指数退避重试，耗尽转人工补偿；DB 定时任务兜底对账
- **MQ 容错**：消费异常指数退避重试（2s→6s→18s 共 4 次），耗尽拒绝不回队，由幂等的 DB 兜底任务接管
- **达梦 DM8**：官方镜像一键启动，`schema.sql` 一体化全量建表 + 种子数据

## 技术栈

| 分类     | 技术                                                            | 版本            |
|--------|---------------------------------------------------------------|---------------|
| 后端框架   | Spring Boot                                                   | 2.3.7.RELEASE |
| 语言     | Java                                                          | 1.8           |
| ORM    | MyBatis-Plus                                                  | 3.3.1         |
| 数据库    | 达梦 DM8（DmJdbcDriver18）                                        | 8.1.2.192     |
| 缓存/锁   | Redis + Redisson                                              | 5.0+ / 3.16.8 |
| 消息队列   | RabbitMQ（Spring AMQP）                                         | 3.7+          |
| 对象存储   | MinIO（io.minio，可选）                                            | 8.5.7         |
| 支付 SDK | WechatPay APIv3 0.3.0 / wxpay-sdk 0.0.3 / Alipay SDK 4.22.57  | —             |
| API 文档 | Swagger                                                       | 2.7.0         |
| 商城前端   | React 18 + Vite 5 + Ant Design 5 + qrcode.react               | —             |
| 管理前端   | React 18 + Vite 5 + Ant Design 5 + SheetJS(xlsx) + Luckysheet | —             |

## 项目结构

```
mall/
├── backend/                       # 后端 Spring Boot 应用（cc.ivera）
│   ├── src/main/java/cc/ivera/
│   │   ├── controller/            # 24 个 REST 控制器（商城 /api/* + 管理 /api/admin/*）
│   │   ├── service/               # 业务服务（交易/退款/库存/对账/wxpay 门面/物流）
│   │   ├── entity/ enums/         # 19 张表实体、14 个状态机/业务枚举
│   │   ├── mapper/                # Mapper 接口（XML 见 resources/mapper/）
│   │   ├── security/              # AuthInterceptor、JwtTokenService、LoginGuardService
│   │   ├── mq/                    # RabbitMQ 消费者（延迟关单、退款状态同步）
│   │   ├── job/                   # 定时任务（关单兜底、退款同步兜底、本地消息重投、模拟物流）
│   │   ├── lock/                  # DistributedLockTemplate（Redisson 实现）
│   │   └── config/                # Redisson/MQ 拓扑/支付配置加载/Swagger/WebMvc
│   ├── src/main/resources/
│   │   ├── mapper/                # MyBatis XML（18 个）
│   │   └── application.yml        # 连接参数与业务配置
│   ├── docs/                      # DM8 与 RabbitMQ 运维手册
│   └── env/
│       ├── docker-compose.dm8.yml # DM8 容器（端口 5236，含健康检查与初始化挂载）
│       └── sql/dm8/               # schema.sql 一体化全量建表 + 种子数据（唯一 SQL 文件）
├── user-ui/                       # 用户商城（React，移动 App 风格，dev 端口 3000）
├── admin-ui/                      # 管理后台（React，dev 端口 3002）
├── AGENTS.md                      # 项目规则（issue 分类/分支命名/测试要求/DoD）
├── CODE_INTRO.md                  # 代码导览（架构/实体/路由/服务/MQ/前端）
└── README.md
```

## 快速启动

### 环境要求

- JDK 1.8+、Maven 3.6+
- Docker（用于达梦 DM8）；Redis 5.0+、RabbitMQ 3.7+（自备或修改连接指向已有实例）
- Node.js 18+

### 1. 启动达梦 DM8（Docker）

```powershell
cd backend/env
docker compose -f docker-compose.dm8.yml up -d
```

容器 healthy 后初始化数据库。用 DM 管理工具连接 `localhost:5236`（默认 `SYSDBA / Cpc2026#@Dm`，schema `SYSDBA`），执行
`backend/env/sql/dm8/schema.sql`（一体化全量脚本，可重复执行，等同清库重建）：

- 核心业务表（20 张）+ 种子数据（管理员账号、支付渠道/应用、示例商品）
- 三级行政区划表 `t_region` 与全国数据
- 收货地址表 `t_shipping_address`

> 全新建库与存量库升级统一使用上述 schema.sql（历史增量结构已全部并入）；已废弃的遗留表（旧对账三表、t_stock_operation_log）仅保留
> DROP 守卫清理，不再重建。
> 数据卷 `dm8-data` 保存数据库数据，**不要执行 `docker compose down -v`**，否则数据丢失需重新建表。

### 2. 配置并启动后端

`backend/src/main/resources/application.yml` 中的默认连接指向部署环境 `192.168.1.200`（DM8 / Redis / RabbitMQ /
MinIO），本地运行请按实际情况修改：

- **DM8** 支持环境变量覆盖：`DM_HOST`、`DM_PORT`、`DM_SCHEMA`、`DM_USERNAME`、`DM_PASSWORD`
- **Redis / RabbitMQ**：直接修改 `spring.redis.*`、`spring.rabbitmq.*`
- **JWT 密钥**：生产必须用环境变量 `AUTH_JWT_SECRET` 覆盖默认值
- **Excel 存储**：`stock.import.storage` 设为 `local`（配合 `stock.import.dir`）或 `minio`（`STOCK_IMPORT_MINIO_*` 环境变量覆盖）
- **支付商户参数**（微信私钥、支付宝密钥等）不在配置文件中，初始化后可在管理后台「支付配置」中维护（存于 `t_payment_channel`）

```powershell
cd backend
mvn spring-boot:run
```

后端须在 DM8 容器健康检查通过后再启动。

### 3. 启动前端

```powershell
# 用户商城 → http://localhost:3000
cd user-ui
npm install
npm run dev

# 管理后台 → http://localhost:3002
cd admin-ui
npm install
npm run dev
```

前端通过 CORS 直连后端 `http://localhost:8080`（见各工程 `src/utils/request.js` 的 baseURL）。

### 4. 访问地址

| 服务         | 地址                                    |
|------------|---------------------------------------|
| 后端 API     | http://localhost:8080                 |
| Swagger 文档 | http://localhost:8080/swagger-ui.html |
| 用户商城       | http://localhost:3000                 |
| 管理后台       | http://localhost:3002                 |

### 开发账号

- 管理员：`admin / Admin@123456`（仅可登录管理后台与用户管理接口，禁止购物车/下单）
- 普通用户：注册入口在商城登录页
- **生产部署必须替换默认凭据、数据库口令与 `AUTH_JWT_SECRET`**

## 对账功能

管理员从微信支付商户平台下载交易账单 CSV → 在「对账管理」页面上传 → 系统同步解析入库并与本地支付/退款记录核对 →
差异单人工标记处理。全链路幂等，不走 MQ。

| 账单种类    | 内容        | 对账范围    | 互斥规则                   |
|---------|-----------|---------|------------------------|
| ALL     | 支付+退款+撤销行 | 支付+退款双向 | 与 SUCCESS/REFUND 互斥    |
| SUCCESS | 仅支付成功行    | 仅支付方向   | 与 ALL 互斥，可与 REFUND 并存  |
| REFUND  | 仅退款行      | 仅退款方向   | 与 ALL 互斥，可与 SUCCESS 并存 |

**8 类差异**：PAY/REFUND × CHANNEL_ONLY（账单有本地无）/ LOCAL_ONLY（本地有账单无）/ AMOUNT_MISMATCH / STATUS_MISMATCH
**5 层幂等**：文件 SHA-256 → 账单日+种类互斥 → Redisson 分布式锁 → 数据库唯一约束 → RECONCILED 幂等

完整数据模型与流程设计见 [CODE_INTRO.md](CODE_INTRO.md)「对账架构」章节。

## 测试

```powershell
# 前端逻辑单测（Node 内置 test runner，仅 user-ui）
cd user-ui   && npm run test:logic    # Token 单飞刷新

# 前端构建
cd user-ui   && npm run build
cd admin-ui  && npm run build

# 后端：当前无自动化测试套件（backend/src/test 为空）。
# 按 AGENTS.md 规则，重构遗留行为前需先在 backend/src/test 补特征测试，之后用 mvn test 回归。
```

## 运维文档

- [backend/docs/DAMENG_DM8_OPERATIONS.md](backend/docs/DAMENG_DM8_OPERATIONS.md) — DM8 启动/初始化/清库重建
- [backend/docs/RABBITMQ_OPERATIONS.md](backend/docs/RABBITMQ_OPERATIONS.md) — 队列拓扑、可靠性与冒烟测试清单
