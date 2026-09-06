# ADMIN_ORDER_EXCEPTION_HANDLING_SPEC — 管理员订单管理与异常处理

- 状态：`implemented`（2026-09-05：后端 3 端点 + RefundApplyVO.failReason + 前端 React/Vue 订单管理 Tab + 退款失败原因展示，91 项单测全绿，两端 build 通过）
- 日期：2026-09-05
- 问题分类：Design change（管理员能力边界 + API 契约 + 前端工作流变更）
- 需求来源：用户报告"看不到订单支付状态、退款退不回来、管理员看不到普通用户订单"

## 1. 背景与问题

### 1.1 用户侧
- 订单列表虽有四维状态标签，但退款失败后**看不到失败原因**（RefundApplyVO 不含 failReason，渠道错误存在 RefundInfo.contentReturn）。
- 微信支付弹窗关闭后需手动刷新才能看到支付状态变化（已有轮询但仅弹窗打开期间生效）。

### 1.2 管理员侧
- **无全部订单视图**：`GET /api/admin/order/wait-ship` 仅返回 PAID+WAIT_SHIP 订单，看不到未支付/已关闭/退款中/异常状态订单。
- **无强制关单能力**：未支付超时订单如调度器未及时处理，管理员无法手动关闭释放库存。
- **无手动标记支付**：渠道回调失败导致订单卡在未支付但用户实际已付款时，管理员无法手动修复。
- **退款列表无失败原因**：管理员退款受理 Tab 看不到渠道返回的具体错误信息。

## 2. 目标

1. 管理员可查看全部用户全部状态订单，支持按状态筛选。
2. 管理员可对未支付订单强制关单（走本地 V2 关单链路）。
3. 管理员可对未支付订单手动标记支付成功（用于回调异常修复）。
4. 用户和管理员均能看到退款失败的具体原因。

## 3. API 契约变更

### 3.1 新增端点

| 端点 | 方法 | 角色 | 说明 |
|---|---|---|---|
| `/api/admin/order/all` | GET | ROLE_ADMIN | 全部订单列表，可选 query 参数 `payStatus`/`orderStatus`/`fulfillmentStatus`/`refundStatus` 筛选，返回 `List<OrderDetailVO>`（含 items）。 |
| `/api/admin/order/{orderNo}/force-close` | POST | ROLE_ADMIN | 强制关单：仅 payStatus=UNPAID 且 orderStatus=WAIT_PAY 可操作，CAS WAIT_PAY→CLOSED + paymentOrderMapper.closeActiveByOrderNo + releaseReservation。 |
| `/api/admin/order/{orderNo}/mark-paid` | POST | ROLE_ADMIN | 手动标记支付成功：仅 payStatus=UNPAID 且 orderStatus=WAIT_PAY 可操作，CAS WAIT_PAY→ACTIVE(payStatus=PAID) + commitReservation。 |

### 3.2 VO 变更

| VO | 变更 | 说明 |
|---|---|---|
| `RefundApplyVO` | 新增 `String failReason` 字段 | detail()/details() 中查 RefundInfo.contentReturn 填充（status=FAILED 时有值，其余为 null）。 |

### 3.3 兼容性影响
- 新增端点不影响现有端点。
- RefundApplyVO 新增字段为 nullable，前端旧版本忽略即可，向后兼容。

## 4. 实现锚点

### 4.1 后端

#### AdminOrderShipmentController（扩展）
- `GET /all`：调 `CheckoutServiceImpl.listAllForAdmin(filters)` 或直接 `orderInfoMapper.selectList(QueryWrapper)` + items 组装。
- `POST /{orderNo}/force-close`：调 `OrderInfoServiceImpl.updateStatusByOrderNoIfStatus(orderNo, NOTPAY, CLOSED)`。
- `POST /{orderNo}/mark-paid`：调 `OrderInfoServiceImpl.updateStatusByOrderNoIfStatus(orderNo, NOTPAY, SUCCESS)`。

#### RefundApplyVO（扩展）
- 新增 `private String failReason;`
- `RefundOrderServiceImpl.detail()` / `details()`：查 `RefundInfoMapper.selectOne(refundNo)` 取 `contentReturn` 填入。

#### OrderInfoServiceImpl（复用现有 CAS）
- `updateStatusByOrderNoIfStatus` 已支持 NOTPAY→CLOSED（关单+释放预占）和 NOTPAY→SUCCESS（标记支付+提交预占），直接复用。

### 4.2 前端

#### 管理员控制台（AdminConsole.jsx / .vue）
- 新增「订单管理」Tab：
  - 表格列：订单号、用户ID、商品、金额、支付状态、履约状态、退款状态、创建时间、操作。
  - 筛选：下拉选 payStatus（全部/未支付/已支付）、orderStatus（全部/待支付/已关闭/已激活）。
  - 操作按钮：未支付订单可「强制关单」「标记已付」。
  - 调 `GET /api/admin/order/all`、`POST /api/admin/order/{orderNo}/force-close`、`POST /api/admin/order/{orderNo}/mark-paid`。

#### 退款记录页（RefundApplications.jsx / .vue）
- 退款状态为 FAILED 的行展示 failReason 列（红字）。
- 管理员退款受理 Tab 同样展示 failReason。

## 5. 测试锚点

- [x] AdminOrderExceptionHandlingTest：force-close 成功/非未支付拒绝/mark-paid 成功/非未支付拒绝/CAS 竞态。
- [x] AdminOrderExceptionHandlingTest：force-close 成功/非未支付拒绝/mark-paid 成功/非未支付拒绝/CAS 竞态/listAllOrders 筛选（6 例）。
- [x] React/Vue build 通过。

## 6. 验收标准

1. [x] 管理员全部订单列表端点 `GET /api/admin/order/all` 返回所有用户订单，含支付/履约/退款状态与商品明细，支持 payStatus/orderStatus/fulfillmentStatus 筛选。
2. [x] 管理员强制关单 `POST /api/admin/order/{orderNo}/force-close`：未支付订单 → CLOSED + 库存释放；非未支付订单 → 拒绝（AdminOrderShipmentController L66-76）。
3. [x] 管理员标记支付 `POST /api/admin/order/{orderNo}/mark-paid`：未支付订单 → PAID + 库存提交；非未支付订单 → 拒绝（AdminOrderShipmentController L78-88）。
4. [x] 退款列表（用户+管理员）FAILED 行展示渠道失败原因（RefundApplyVO.failReason 从 RefundInfo.contentReturn 填充；前端 RefundApplications.jsx/AdminConsole.vue/Orders.vue 红字展示）。
5. [x] 前端 AdminConsole 新增「订单管理」Tab（React AdminConsole.jsx + Vue AdminConsole.vue），全部订单列表 + 状态筛选 + 强制关单/标记已付操作按钮。
6. [x] 单测 91 项全绿（含新增 AdminOrderExceptionHandlingTest 6 例）、React/Vue build 通过。
7. [x] 退款渠道发起对账兜底（2026-09-05 实测修复）：订单已 PAID 但 PaymentOrder 卡在 PAYING（旧代码支付成功未推进支付单状态），`RefundOrderServiceImpl.findSuccessPaymentOrder` 增加 PAYING→SUCCESS 对账推进逻辑：先查 SUCCESS，找不到时查 PAYING 并 CAS 推进为 SUCCESS（paidAmount=requestAmount），修复后 confirm-return 不再报"未找到成功支付单"。
8. [x] 管理员主动查询渠道退款状态（2026-09-05）：`POST /api/admin/refund/{refundNo}/query-status` → `RefundOrderServiceImpl.queryRefundStatus` 委托 V1 `RefundApplicationService.queryRefundStatus` 向渠道查单并更新 RefundInfo，随后映射 V1 RefundStatus→V2 RefundOrderStatus（SUCCESS→SUCCESS / FAILED+ABNORMAL→FAILED）同步 V2 退款单状态并返回 RefundApplyVO（含 failReason）；前端 React/Vue AdminConsole 退款受理 Tab 对 REFUNDING/FAILED 状态退款单新增「查询状态」按钮（loading 提示 + 成功展示最新状态 + 失败展示错误原因）。
