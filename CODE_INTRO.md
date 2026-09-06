# CODE_INTRO — Payment Demo 代码导览

> 本文是后端与四端前端的架构/代码导览。快速启动与环境要求见 [README.md](README.md)。

## 1. 项目概述

Payment Demo 是基于 Spring Boot 的支付业务全链路演示项目，集成**微信支付 V2/V3** 与**支付宝**沙箱通道，覆盖购物车、下单、支付、发货物流、确认收货、分项退款、账单对账完整闭环，并实现三层并发控制、库存预占、登录防护与批量库存维护等企业级能力。

- **技术栈**：Spring Boot 2.3.7 + Java 1.8 + MyBatis-Plus 3.3.1
- **数据库**：达梦 DM8（DmJdbcDriver18，大小写不敏感，非引号表名）
- **中间件**：Redis（缓存 + Redisson 分布式锁）、RabbitMQ（延迟/重试/死信）
- **对象存储**：本地磁盘 或 MinIO（库存 Excel 导入文件，可配置切换）
- **前端**：4 个独立工程（React/Vue × 商城/管理后台）
- **API 文档**：Swagger 2.7.0（`/swagger-ui.html`）

## 2. 项目结构

```
payment-demo-java-dm/
├── payment-demo/                      # 后端 Spring Boot 应用
│   ├── src/main/java/cc/ivera/
│   │   ├── config/                    # 配置类（Redisson/MQ/支付客户端/MinIO/Swagger/WebMvc）
│   │   ├── controller/                # 22 个 REST 控制器
│   │   ├── service/                   # 业务服务（交易/退款/库存/对账/导入/履约）
│   │   ├── entity/                    # 实体（20 张表）
│   │   ├── enums/                     # 状态机枚举
│   │   ├── mapper/                    # MyBatis-Plus Mapper（BaseMapper，无 XML）
│   │   ├── dto/ vo/                   # 请求 DTO 与响应 VO
│   │   ├── security/                  # AuthInterceptor、LoginGuardService、AuthContext
│   │   ├── mq/                        # RabbitMQ 生产者/消费者/延迟配置
│   │   ├── job/                       # 定时任务（超时关单、模拟物流）
│   │   ├── lock/                      # DistributedLockTemplate（Redisson 实现）
│   │   ├── event/                     # 领域事件（退款额度释放/退款成功监听）
│   │   ├── exception/ handler/        # BizException/OversoldException + 全局异常处理
│   │   └── util/                      # 工具类
│   ├── src/main/resources/
│   │   ├── mapper/                    # MyBatis XML（账单模块无 XML）
│   │   └── application.yml            # 应用配置
│   └── env/
│       ├── docker-compose.dm8.yml     # DM8 容器编排（5236 端口 + 健康检查 + dm8-data 卷）
│       └── sql/dm8/payment_demo.sql   # 全量建表 + 种子数据（20 张业务表）
├── payment-demo-react/                # 用户商城：React 18 + Vite + AntD5（dev :3000）
├── payment-demo-vue/                  # 用户商城：Vue2 + Element UI（dev :3000）
├── payment-demo-react-admin/          # 管理后台：React 18 + Vite + AntD5 + Luckysheet（dev :3002）
├── payment-demo-vue-admin/            # 管理后台：Vue2 + Element UI + Luckysheet（dev :3003）
├── spec/                              # 规格状态账本
└── AGENTS.md                          # 项目规则（issue 分类/分支/spec 治理/DoD）
```

## 3. 核心架构

### 3.1 三层并发控制

| 层级 | 机制 | 说明 |
|------|------|------|
| 第一层 | 通知幂等检查（Redis + notifyId） | 防止重复处理支付/退款通知 |
| 第二层 | Redisson 分布式锁（看门狗自动续期） | 防止并发操作同一订单/账单日 |
| 第三层 | 数据库行锁 + CAS 状态更新（version/条件更新） | 最终一致性保障，冲突返回友好提示 |

### 3.2 认证与授权（security 包）

- **双 Token**：Access 30 分钟 + Refresh 7 天；401 时前端 single-flight 单飞刷新，避免并发重复刷新
- **AuthInterceptor**：统一拦截 `/api/**` 校验 Token 并注入 AuthContext
- **角色规则**：`ROLE_ADMIN` 禁止访问 `/api/cart`、`/api/checkout`（返回 403）
- **登录锁定 LoginGuardService**：连续 3 次密码错误，按「用户名 + IP」双维度锁定 10 分钟（Redis key：`auth:login_lock:*`）
- **密码重置**：`t_password_reset_request` 记录用户找回申请；管理员受理生成随机密码（仅展示一次）或直接按 userId 重置，重置后旧 Token 全局失效

### 3.3 交易模型 V2

订单状态拆分为三条正交状态线（前端 `statusLabels.js` 统一文案）：

| 状态线 | 枚举 | 取值 |
|--------|------|------|
| 支付状态 | `PayStatus` | UNPAID / PAID |
| 生命周期 | `OrderLifecycleStatus` | WAIT_PAY / ACTIVE / CLOSED |
| 履约状态 | `FulfillmentStatus` | WAIT_SHIP / SHIPPED / RECEIVED / CANCELLED |
| 退款状态 | `OrderRefundStatus` | NONE / REFUNDING / PARTIAL_REFUNDED / FULL_REFUNDED |

- **t_payment_order**：支付单（与订单 1:1 扩展），记录渠道支付单号与支付单状态
- **t_order_info**：主订单（含 version 乐观锁、pay_status、三条状态线）
- **t_order_item**：订单明细，**快照价**（下单时价格/标题固化，不受后续改价影响）
- 关单：RabbitMQ 延迟消息（`payment.order.close-delay-ms` 默认 900000ms）+ 定时任务兜底；强制关单幂等（已关闭直接返回成功）

### 3.4 库存预占模型（InventoryService）

```
下单 → reservation LOCKED（available -= qty, locked += qty，CAS 防超卖）
  ├─ 支付成功 → COMMITTED（locked -= qty；写库存流水/支付后扣减日志）
  └─ 关单/取消 → RELEASED（locked 归还 available）
```

- `t_inventory_reservation`：预占单（LOCKED/COMMITTED/RELEASED 状态机）
- `t_inventory_transaction`：库存流水（含 FAILED 记录：operation_status/available_delta/error_message）
- `t_stock_operation_log`：库存操作日志（业务类型 InventoryBizType：下单/支付/退款/手工调整/Excel 入库等）
- 超卖保护：CAS 扣减失败 → MQ 重试 3 次 → 死信队列 → 管理后台「MQ/库存异常」页重放；支付后扣减失败自动全额退款

### 3.5 订单履约与退款

- **t_order_shipment**：发货单（运单号、发货时间、物流时间线）；`MockLogisticsSimulationJob` 定时模拟物流推进
- 用户端：查看物流时间线、确认收货；已付款未发货取消订单自动生成退款申请
- 管理端：模拟发货、强制关单、退款受理
- **退款单模型**（RefundOrder/RefundItem，区别于旧的渠道退款记录 t_refund_info）：
  - 类型 `RefundType`（6 种）：未发货取消（受理后自动补库存）、退货退款（签收质检后补库存）、仅退款（不补库存）、差价退款（管理员发起手填金额）、重复支付/晚到支付自动原路退款（系统冲正，不占售后额度）
  - 状态 `RefundOrderStatus`：APPLYING（待审核，可编辑/撤销）→ 受理后冻结额度 → 审核/退货签收 → 渠道退款 → SUCCESS/FAILED
  - **防超退**：受理即冻结可退额度，前端 `refundQuota.js` 与后端 RefundPolicy 双重核算；最后一件吃尾差，金额以服务端为准

### 3.6 支付配置体系

支付参数从数据库动态加载，支持管理后台页面维护（无需重启）：

- `t_payment_channel` — 支付渠道公共配置（WXPAY/ALIPAY）
- `t_payment_app` — 渠道下商户/应用配置（appid/mchId/密钥/证书等）
- 下单时从 `/api/payment-app` 加载启用应用，订单绑定 `payment_app_id` + `payment_channel_code`
- 通知按订单绑定的配置校验金额/商户号/appId；配置缓存可通过 `/api/payment-config/reload` 刷新

### 3.7 批量库存维护与 Excel 存储

- 前端「批量库存维护」页：下载模板 → 导入 .xlsx → 生成 `t_stock_import` 导入记录（PENDING/IMPORTED，CAS 防重复确认）
- 点选记录 → 新浏览器页签打开全屏 Luckysheet 编辑器（隐藏公共头尾）核对/编辑保存 → 回列表确认入库
- 确认入库逐条独立原子事务（CAS 库存更新 + MANUAL_ADJUST 流水），失败行返回原因不影响成功行
- 库存流水分页接口支持商品/业务类型/状态过滤（页大小 1–100）
- **文件存储抽象 V6**：
  - `StockImportFileStore` 接口 + `LocalStockImportFileStore`（`stock.import.dir`）+ `MinioStockImportFileStore`（`stock.import.minio.*`，环境变量 `STOCK_IMPORT_MINIO_*` 覆盖）+ `StockImportFileStorage` 路由门面
  - `t_stock_import.storage_type/file_path` 按记录持久化后端与地址；读取按记录自身 storage_type 路由（null → LOCAL 兼容旧记录）；本地实现无条件装配以支持历史 LOCAL 记录读取

### 3.8 对账架构（账单上传 + 同步对账）

对账采用「管理员下载微信交易账单 CSV → 页面上传 → 系统同步解析入库并对账」模式，不走 MQ、无定时调度。

#### 账单种类与解析（依据微信支付 v3《交易账单详细说明》）

`WxTradeBillParser`（无 Spring 依赖，可独立单测）按表头列名定位字段而非固定下标，自动识别种类：

| 种类 | 识别条件 | 内容 | 对账范围 |
|------|----------|------|----------|
| ALL | 表头含「微信退款单号」列 | 支付行 + 退款行 + 撤销行（27 列） | 支付 + 退款双向 |
| SUCCESS | 表头无退款相关列 | 仅支付成功行（20 列） | 仅支付方向 |
| REFUND | 表头含「退款申请时间」/「退款成功时间」列 | 仅退款行（29 列） | 仅退款方向 |

解析规则：
- 行类型由「交易状态」列判定：`SUCCESS`=支付行，`REFUND`=转入退款行，`REVOKED`=付款码撤销行
- 字段值前反引号 `` ` `` 前缀（防 Excel 科学计数法）解析时去除；金额元→分用 BigDecimal `movePointRight(2)`
- 表头/汇总行/首字段非交易时间行一律跳过；坏行计数跳过不中断整单
- BOM 清洗、双引号包裹与 `""` 转义兼容；撤销行无退款单号时回退原支付订单号

#### 数据模型（三表）

- **t_bill_import**：批次表。import_no（BILL+序号，UK）、channel_code、bill_kind（ALL/SUCCESS/REFUND）、bill_date、file_hash（SHA-256，UK）、笔数统计、status（IMPORTED/RECONCILED/FAILED）。唯一约束：uk(import_no)、uk(渠道,类型,日期,种类)、uk(file_hash)
- **t_bill_record**：流水表。record_type（PAY/REFUND）、channel_serial_no（支付=微信订单号/退款=微信退款单号，UK）、biz_no、金额（分）、交易时间、raw_line（CLOB 原始行）
- **t_bill_reconcile_discrepancy**：差异单表。biz_type、discrepancy_type（8 类）、双方金额/状态快照、status（OPEN/RESOLVED）、resolve_remark（必填）、resolved_by/time

#### 上传与对账流程

```
上传 CSV → 参数校验（非空/≤10MB/历史日期）
  → SHA-256 hash 命中？→ 直接返回原批次（幂等）
  → 解析 CSV（识别 billKind）→ 账单日+种类互斥校验（锁外快速拒绝）
  → Redisson 锁 bill-reconcile:WXPAY:{billDate}（等5s/租60s）
      ├─ 锁内二次检查
      ├─ 事务1：批次 + 流水入库
      └─ 事务2：对账（失败标 FAILED，批次保留可重跑）
  → 返回批次
```

- **支付核对**：账单 PAY 行 vs t_payment_info（order_no + WXPAY）：本地无→PAY_CHANNEL_ONLY；金额不符→PAY_AMOUNT_MISMATCH；本地非 SUCCESS→PAY_STATUS_MISMATCH；反向扫描账单日本地 SUCCESS 但账单无→PAY_LOCAL_ONLY
- **退款核对**：账单 REFUND 行 vs t_refund_info（refund_no）：同理四类，PROCESSING 中间态不判差异
- **重跑**：先按 import_id 删除旧差异单再重算，结果确定不重复
- **金额口径**：账单元→分 BigDecimal 精确转换；本地取 payer_total / refund（分），Integer 直接比较

## 4. 数据库实体（20 张业务表）

| 实体 | 表名 | 说明 |
|------|------|------|
| `PaymentChannel` | `t_payment_channel` | 支付渠道配置（WXPAY/ALIPAY） |
| `PaymentApp` | `t_payment_app` | 商户/应用配置 |
| `Product` | `t_product` | 商品（价格分、库存、上下架状态） |
| `OrderInfo` | `t_order_info` | 主订单（version 乐观锁 + 三条状态线） |
| `OrderItem` | `t_order_item` | 订单明细（快照价） |
| `PaymentOrder` | `t_payment_order` | 支付单（渠道单号/支付单状态） |
| `PaymentInfo` | `t_payment_info` | 渠道支付记录（payer_total 分） |
| `OrderShipment` | `t_order_shipment` | 发货单（运单号/物流时间线） |
| `InventoryReservation` | `t_inventory_reservation` | 库存预占（LOCKED/COMMITTED/RELEASED） |
| `InventoryTransaction` | `t_inventory_transaction` | 库存流水（含 FAILED 记录） |
| `StockOperationLog` | `t_stock_operation_log` | 库存操作日志（业务类型） |
| `StockImport` | `t_stock_import` | Excel 导入记录（storage_type/file_path） |
| `RefundOrder` / `RefundItem` | `t_refund_order` / `t_refund_item` | 退款单/退款明细（冻结额度防超退） |
| `RefundInfo` | `t_refund_info` | 渠道退款记录（refund 分） |
| `UserAccount` | `t_user` | 用户账户（角色/禁用状态） |
| `PasswordResetRequest` | `t_password_reset_request` | 找回密码申请 |
| `BillImport` | `t_bill_import` | 账单批次（hash/种类幂等） |
| `BillRecord` | `t_bill_record` | 账单流水（PAY/REFUND 行） |
| `BillReconcileDiscrepancy` | `t_bill_reconcile_discrepancy` | 对账差异（8 类 + 快照 + 处理记录） |
| `BaseEntity` | — | 公共基类（id/createTime/updateTime） |

## 5. Controller 路由（22 个）

**商城端**

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `AuthController` | `/api/auth` | 注册/登录/登出/双 Token 刷新/改密 |
| `ProductController` | `/api/product` | 商品列表/详情 |
| `CartController` | `/api/cart` | Redis 购物车（管理员禁入） |
| `CheckoutController` | `/api/checkout` | 多商品结算下单（管理员禁入） |
| `OrderInfoController` | `/api/order-info` | 订单查询 |
| `OrderShipmentController` | `/api/order` | 物流时间线/确认收货/用户取消 |
| `RefundApplyController` | `/api/refund-applies` | 分项退款申请/编辑/撤销 |
| `RefundInfoController` | `/api/refund-info` | 退款记录查询 |
| `WxPayController` | `/api/wx-pay` | 微信支付 V3（Native 扫码/退款/通知/账单） |
| `WxPayV2Controller` | `/api/wx-pay-v2` | 微信支付 V2（扫码/通知） |
| `AliPayController` | `/api/ali-pay` | 支付宝（扫码/退款/通知/账单） |
| `PaymentAppController` | `/api/payment-app` | 启用支付应用列表 |
| `PaymentChannelController` | `/api/payment-channel` | 支付渠道配置 |
| `PaymentConfigController` | `/api/payment-config` | 配置缓存重载 |
| `TestController` | `/api/test` | 测试接口 |

**管理端（/api/admin，仅 ROLE_ADMIN）**

| Controller | 路径前缀 | 说明 |
|------------|----------|------|
| `AdminUserController` | `/api/admin/users` | 用户列表/禁用启用/在线状态/详情 |
| `AdminProductController` | `/api/admin/products` | 商品 CRUD/库存调整/上下架/批量调整/库存日志 |
| `AdminOrderShipmentController` | `/api/admin/order` | 订单管理/模拟发货/强制关单（幂等） |
| `AdminRefundOrderController` | `/api/admin/refund` | 退款受理/拒绝/退货签收/渠道重试 |
| `StockAdminController` | `/api/admin/stock` | 库存流水分页/Excel 导入确认/异常重放 |
| `AdminPasswordResetRequestController` | `/api/admin/password-reset-requests` | 找回密码申请受理/拒绝 |
| `ReconciliationController` | `/api/reconciliation` | 账单上传/批次/差异/重跑/标记处理 |

## 6. Service 层要点

| Service | 说明 |
|---------|------|
| `AuthService` / `LoginGuardService` | 双 Token 认证、登录失败计数与锁定 |
| `ProductService` | 商品 CRUD、库存 CAS 调整 |
| `CartService` | Redis 购物车（实时价/库存） |
| `CheckoutService` | 多商品下单（快照价 + 库存预占 + 支付单创建） |
| `OrderInfoService` | 订单状态机、关单幂等 |
| `InventoryService` | 预占 LOCKED→COMMITTED/RELEASED，CAS 防超卖 |
| `OrderShipmentService` | 发货/物流时间线/确认收货 |
| `RefundOrderService` / `RefundPolicy` | 退款单状态机、额度冻结与防超退核算 |
| `RefundApplyService` | 用户分项退款申请/编辑/撤销 |
| `AliPayService` | 支付宝支付/退款/查单/关单 |
| `wxpay/WxPayOrderFacade` | 微信 V3 订单门面 |
| `wxpay/WxPayRefundFacade` | 微信 V3 退款门面 |
| `wxpay/WxPayBillFacade` | 微信账单下载门面 |
| `StockImportService` | Excel 导入记录/确认入库（CAS）/失败明细 |
| `StockImportFileStorage` + Local/Minio Store | 导入文件存储路由（local/minio） |
| `StockOperationService` | 库存扣减/回补/DLQ 重放 |
| `bill/BillReconcileService` | 账单上传对账（幂等/种类互斥/8 类差异） |
| `bill/parser/WxTradeBillParser` | 微信交易账单 CSV 解析 |
| `OrderCloseMessageService` | 订单关闭延迟消息 |
| `RefundStatusSyncMessageService` | 退款状态同步延迟消息 |

## 7. MQ 与定时任务

| 组件 | 队列/周期 | 说明 |
|------|-----------|------|
| `OrderCloseConsumer` | 订单关闭延迟队列 | 超时未支付订单自动关闭（关单延迟可配置） |
| `RefundStatusSyncConsumer` | 退款同步延迟队列 | 延迟同步渠道退款结果 |
| `StockOperationConsumer` | 库存操作队列 | 分布式锁 + 行锁扣减/回补，3 次重试 |
| `StockDeadLetterConsumer` | 库存死信队列 | 重试耗尽落库 NEED_MANUAL，后台可重放 |
| `TimeoutOrderCloseScheduler` | 定时 | 关单兜底扫描 |
| `MockLogisticsSimulationJob` | 定时 | 模拟物流状态推进（发货→运输→送达） |

> 对账不走 MQ：上传后同步解析入库并对账。

## 8. 前端工程

四工程对等实现同一套后端契约；前端 axios baseURL 均为 `http://localhost:8080`（CORS 直连）。

| 工程 | 技术栈 | dev 端口 | 角色 |
|------|--------|----------|------|
| `payment-demo-react` | React 18 + Vite + Ant Design 5 | 3000 | 用户商城（淘宝风格：首页/购物车/订单/退款/用户中心/支付成功） |
| `payment-demo-vue` | Vue 2 + Vue CLI + Element UI 2.15 | 3000 | 用户商城（同上对等） |
| `payment-demo-react-admin` | React 18 + Vite + Ant Design 5 + Luckysheet | 3002 | 管理后台（左侧导航 + 右侧列表 + 抽屉详情） |
| `payment-demo-vue-admin` | Vue 2 + Vue CLI + Element UI + Luckysheet | 3003 | 管理后台（同上对等） |

**商城端页面**：首页商品列表、购物车（空态/吸底结算条）、我的订单（微信扫码弹窗 + 3 秒轮询、分项退款）、退款申请、用户中心（改密/登出）、支付成功页。

**管理后台菜单**：订单管理、订单发货、退款受理、商品库存（含商品详情抽屉）、批量库存维护（Excel 导入 + 全屏 Luckysheet 编辑器新页签路由）、MQ/库存异常、用户列表（详情抽屉 + 在线状态）、密码重置申请、重置用户密码、支付配置、对账管理、下载账单。

**设计规范**：商城 `taobao.css`（橙 #FF5000 主色、等宽数字、焦点态、卡片化骨架）；管理端 `admin.css`（antd token / Element CSS 变量统一主色、页标题强调条 + 白卡骨架）。

## 9. 配置要点（application.yml）

| 配置 | 说明 |
|------|------|
| `spring.datasource.*` | DM8 连接（jdbc:dm://host:5236） |
| `spring.redis.*` | Redis 连接（购物车/Token/锁定/幂等） |
| `spring.rabbitmq.*` | RabbitMQ 连接 |
| `auth.jwt-secret` / `refresh-ttl-seconds` | JWT 密钥（环境变量 AUTH_JWT_SECRET 覆盖）/ Refresh 7 天 |
| `payment.order.close-delay-ms` | 关单延迟（默认 900000 = 15 分钟） |
| `payment.refund.status-sync-delay-ms` | 退款状态同步延迟（默认 60000） |
| `stock.import.storage` | Excel 存储后端：`local` / `minio` |
| `stock.import.dir` | local 模式保存目录 |
| `stock.import.minio.*` | minio 模式 endpoint/access-key/secret-key/bucket（STOCK_IMPORT_MINIO_* 环境变量覆盖） |

## 10. Spec 治理

项目使用 `spec/` 目录作为公共行为与架构契约的状态账本（规则见 [AGENTS.md](AGENTS.md)）：

- `spec/governance/` — 治理规范（issue 分类、PR 检查清单）
- `spec/implemented/` — 已落地行为（trading/frontend/admin 等域，含实现锚点）
- `spec/planned/` — 规划中变更（部分交付需标注剩余验收项）
- `spec/archived/` — 废弃/归档决策（保留原因与日期）

变更代码前必须先找到/创建对应 spec；实现、测试、文档与 spec 不一致即视为未完成。
