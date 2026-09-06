# Payment Demo — 支付集成与并发控制演示项目

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-2.3.7-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java](https://img.shields.io/badge/Java-1.8-orange.svg)](https://www.oracle.com/java/technologies/javase-jdk8-downloads.html)
[![DM8](https://img.shields.io/badge/DM8-达梦数据库-blue.svg)]()

## 项目简介

基于 Spring Boot 的支付业务全链路演示项目：集成**微信支付 V2/V3** 与**支付宝**沙箱通道，覆盖「浏览商品 → 购物车 → 下单 → 支付 → 发货/物流 → 确认收货 → 退款/退货 → 账单对账」完整交易闭环，并实现企业级并发控制、幂等保障、库存预占、分项退款与上传式账单对账。

前端按**用户商城**与**管理后台**两类角色拆分为 4 个独立工程（React + Vue 双栈对等实现），与后端 REST 契约完全对齐。

## 核心特性

**交易链路**
- **微信支付 V3**：扫码支付（Native 二维码轮询）、退款、订单查询、账单下载；**微信支付 V2**：扫码支付与通知
- **支付宝**：扫码支付（表单跳转）、退款、订单查询、账单下载
- **多商品订单**：主子表结构，明细快照价；支付配置（渠道/应用）存数据库动态加载，订单绑定 `payment_app_id`
- **Redis 购物车**：实时读取商品最新价格与库存；管理员角色禁止访问购物车/下单接口
- **库存预占模型**：下单 `LOCKED` 预占 → 支付成功 `COMMITTED` 扣减 / 关单取消 `RELEASED` 回补；超卖自动全额退款
- **订单履约**：待发货 → 模拟发货（运单号）→ 物流时间线 → 确认收货；已付款未发货取消自动生成退款申请
- **分项退款**：6 种退款类型（未发货取消/退货退款/仅退款/差价退款/重复支付/晚到支付自动冲正），待审核可编辑/撤销，受理后冻结额度动态防超退，最后一件吃尾差

**管理后台**
- **退款受理**：受理/拒绝/退货签收/渠道重试/状态查询
- **商品库存**：新增商品、库存调整、上架/下架、库存操作日志
- **批量库存维护**：Excel（.xlsx）导入 → 生成导入记录 → 新页签全屏 Luckysheet 核对/编辑 → 确认入库（CAS 防重复执行）；失败行记录原因，库存流水分页查询
- **Excel 文件存储**：`stock.import.storage=local|minio` 可切换，本地磁盘或 MinIO 对象存储；记录级路由，历史文件按原后端读取
- **用户管理**：分页/搜索、禁用启用、在线状态独立接口、用户详情抽屉、登录锁定（3 次失败锁 10 分钟，用户名+IP 双维度）
- **密码重置**：用户提交找回申请 → 管理员受理生成随机密码（仅展示一次）或直接按 userId 重置
- **账单对账**：上传微信交易账单 CSV（ALL/SUCCESS/REFUND），自动解析入库并对账，8 类差异识别，人工标记处理，全链路幂等

**工程保障**
- **三层并发控制**：通知幂等检查（Redis）→ Redisson 分布式锁 → 数据库行锁 + CAS
- **双 Token 认证**：Access 30 分钟 + Refresh 7 天，401 单飞（single-flight）无感刷新
- **MQ 容错**：库存扣减/回补最多 3 次重试，耗尽入死信队列，管理员可在后台重放
- **达梦 DM8**：官方 Docker 镜像一键启动，`payment_demo.sql` 全量建表（20 张业务表）

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 2.3.7 |
| 语言 | Java | 1.8 |
| ORM | MyBatis-Plus | 3.3.1 |
| 数据库 | 达梦 DM8（DmJdbcDriver18） | 8.x |
| 缓存/锁 | Redis + Redisson | 5.0+ / 3.16.8 |
| 消息队列 | RabbitMQ | 3.7+ |
| 对象存储 | MinIO（io.minio 8.5.7，可选） | — |
| 支付 SDK | WechatPay APIv3 0.3.0 / Alipay SDK 4.22.57 | — |
| API 文档 | Swagger | 2.7.0 |
| 商城前端 | React 18 + Vite + Ant Design 5；Vue 2 + Element UI 2.15 | — |
| 管理前端 | React 18 + Vite + Ant Design 5（Luckysheet）；Vue 2 + Element UI（Luckysheet） | — |

## 项目结构

```
payment-demo-java-dm/
├── payment-demo/                  # 后端 Spring Boot 应用
│   ├── src/main/java/cc/ivera/
│   │   ├── controller/            # 22 个 REST 控制器（商城 /api/* + 管理 /api/admin/*）
│   │   ├── service/               # 业务服务（交易/退款/库存/对账/导入存储）
│   │   ├── entity/ enums/ mapper/ # 实体（20 表）、状态枚举、MyBatis-Plus Mapper
│   │   ├── security/              # 认证拦截、登录锁定 LoginGuardService
│   │   ├── mq/                    # RabbitMQ 生产/消费（关单延迟、退款同步、库存）
│   │   ├── job/                   # 定时任务（超时关单、模拟物流）
│   │   └── config/                # Redis/Redisson/MQ/支付客户端/MinIO 配置
│   ├── src/main/resources/
│   │   └── application.yml        # 连接参数与业务配置（JWT/关单延迟/库存导入存储）
│   └── env/
│       ├── docker-compose.dm8.yml # DM8 容器（端口 5236，含健康检查）
│       └── sql/dm8/payment_demo.sql  # 全量建表 + 种子数据（20 张业务表）
├── payment-demo-react/            # 用户商城（React，淘宝风格 UI，dev 端口 3000）
├── payment-demo-vue/              # 用户商城（Vue2，dev 端口 3000）
├── payment-demo-react-admin/      # 管理后台（React，左导航+列表+抽屉，dev 端口 3002）
├── payment-demo-vue-admin/        # 管理后台（Vue2，dev 端口 3003）
├── spec/                          # 规格状态账本（AGENTS.md 驱动）
├── materials/                     # 行动卡参考材料
├── AGENTS.md                      # 项目规则（issue 分类/分支命名/spec 治理/DoD）
├── CODE_INTRO.md                  # 代码导览（架构/实体/路由/服务/对账详细设计）
└── README.md
```

## 快速启动

### 环境要求

- JDK 1.8+、Maven 3.6+
- Docker（用于达梦 DM8）；或本机已装 DM8 8.x
- Redis 5.0+、RabbitMQ 3.7+
- Node.js 16+

### 1. 启动达梦 DM8（Docker）

```powershell
cd payment-demo/env
docker compose -f docker-compose.dm8.yml up -d
# 容器 healthy 后，用 DM 管理工具执行 sql/dm8/payment_demo.sql 全量建表
```

> 数据卷 `dm8-data` 保存数据库数据，**不要执行 `docker compose down -v`**，否则数据丢失需重新建表。

### 2. 配置并启动后端

修改 `payment-demo/src/main/resources/application.yml` 中的 DM8 / Redis / RabbitMQ 连接（MinIO、JWT 密钥等可用环境变量覆盖），然后：

```powershell
cd payment-demo
mvn spring-boot:run
```

后端须在 DM8 容器健康检查通过后再启动。

### 3. 启动前端（4 选 N，按需）

```powershell
# 用户商城（二选一即可，功能对等；两者 dev 端口均为 3000，勿同时启动）
cd payment-demo-react      && npm install && npm run dev     # React 商城 → http://localhost:3000
cd payment-demo-vue        && npm install && npm run serve   # Vue 商城  → http://localhost:3000

# 管理后台（二选一即可，功能对等）
cd payment-demo-react-admin && npm install && npm run dev    # React 后台 → http://localhost:3002
cd payment-demo-vue-admin   && npm install && npm run serve  # Vue 后台  → http://localhost:3003
```

前端通过 CORS 直连后端 `http://localhost:8080`（见各工程 `src/utils/request.js` 的 baseURL）。

### 4. 访问地址

| 服务 | 地址 |
|------|------|
| 后端 API | http://localhost:8080 |
| Swagger 文档 | http://localhost:8080/swagger-ui.html |
| React 商城 | http://localhost:3000 |
| Vue 商城 | http://localhost:3000 |
| React 管理后台 | http://localhost:3002 |
| Vue 管理后台 | http://localhost:3003 |

### 开发账号

- 管理员：`admin / Admin@123456`（仅可登录管理后台与用户管理接口，禁止购物车/下单）
- 普通用户：注册入口在商城登录页；**生产部署必须替换默认凭据与 `AUTH_JWT_SECRET`**

## 对账功能

管理员从微信支付商户平台下载交易账单 CSV → 在「对账管理」页面上传 → 系统同步解析入库并与本地支付/退款记录核对 → 差异单人工标记处理。全链路幂等，不走 MQ。

| 账单种类 | 内容 | 对账范围 | 互斥规则 |
|------|------|----------|----------|
| ALL | 支付+退款+撤销行 | 支付+退款双向 | 与 SUCCESS/REFUND 互斥 |
| SUCCESS | 仅支付成功行 | 仅支付方向 | 与 ALL 互斥，可与 REFUND 并存 |
| REFUND | 仅退款行 | 仅退款方向 | 与 ALL 互斥，可与 SUCCESS 并存 |

**8 类差异**：PAY/REFUND × CHANNEL_ONLY（账单有本地无）/ LOCAL_ONLY（本地有账单无）/ AMOUNT_MISMATCH / STATUS_MISMATCH
**5 层幂等**：文件 SHA-256 → 账单日+种类互斥 → Redisson 分布式锁 → 数据库唯一约束 → RECONCILED 幂等

完整数据模型与流程设计见 [CODE_INTRO.md](CODE_INTRO.md)「对账架构」章节。

## 测试

```powershell
# 后端：特征测试 + 单元测试
cd payment-demo
mvn test
# 行为保持型重构回归：
mvn "-Dtest=PublicApiCharacterizationTest,InfrastructureBehaviorCharacterizationTest" test

# 前端工具函数单测（Node 内置 test runner）
cd payment-demo-react      && npm test
cd payment-demo-vue        && npm test

# 前端构建
cd payment-demo-react       && npm run build
cd payment-demo-vue         && npm run build
cd payment-demo-react-admin && npm run build
cd payment-demo-vue-admin   && npm run build
```

## Spec 治理

项目使用 `spec/` 目录作为公共行为与架构契约的状态账本（规则详见 [AGENTS.md](AGENTS.md)）：

- `spec/governance/` — 治理规范（issue 分类、PR 检查清单）
- `spec/implemented/` — 已落地行为（交易模型 V2、管理端 UI 隔离、库存导入存储、UI 品味重设计等）
- `spec/planned/` — 规划中变更
- `spec/archived/` — 废弃/归档决策
