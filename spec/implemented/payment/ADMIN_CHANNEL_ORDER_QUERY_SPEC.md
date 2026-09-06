# 管理端渠道订单查询与主动同步规范（Admin Channel Order Query）

> State: planned
> Updated: 2026-09-06
> Issue class: Design change + 新增管理端 Public API
> 状态: 方案已与用户确认（统一端点 + 同步成功状态，不自动关单）

## 1. 背景与问题

管理端订单页（AdminOrders）此前没有"渠道订单查询"能力：

- 管理员看不到三方渠道侧（微信/支付宝）的交易状态。
- 用户已支付但回调丢失时，管理员只能"手动标记支付成功"（人工判断，无渠道凭证依据）。

后端已存在查单能力但未暴露给管理端：

- 微信：`WxPayOrderFacade.queryPaymentStatus(orderNo)` —— 查 V3 单，TRADE_SUCCESS 走统一成功链路落库，仅挂在用户端 `GET /api/wx-pay/query/status/{orderNo}`。
- 支付宝：`AliPayServiceImpl.checkOrderStatus(orderNo)` —— 对账链路内部使用，未暴露 HTTP 端点；其"未支付→关单"语义属于超时对账，不适合管理端查单复用。

## 2. 设计决策（已确认）

1. **一个统一管理端端点**：`GET /api/admin/order/{orderNo}/channel-query`，后端按订单 `payment_type` 自动路由微信/支付宝。
2. **同步成功状态**：渠道返回已支付成功时，自动走统一支付成功链路（幂等）推进本地订单；**不自动关单**——未支付/渠道无单仅展示状态，关单仍由已有"强制关单"按钮或超时对账负责，避免管理员误操作。

## 3. 数据模型

无库表变更，只读查询 + 复用支付成功处理器。

## 4. 接口契约

### GET /api/admin/order/{orderNo}/channel-query

权限：管理端（AuthInterceptor 拦截 `/api/admin/**`）。

响应 `R<ChannelOrderQueryVO>`：

| 字段 | 说明 |
|---|---|
| orderNo | 订单号 |
| channelCode | WXPAY / ALIPAY |
| channelTradeState | 渠道侧交易状态（微信 trade_state / 支付宝 trade_status；无法确认时 UNKNOWN） |
| channelTradeStateDesc | 渠道状态中文描述 |
| localOrderStatusBefore | 查单前本地订单状态（order_status） |
| localOrderStatusAfter | 查单后本地订单状态 |
| localPayStatusAfter | 查单后本地支付状态（pay_status） |
| synced | 本次查单是否推进了本地订单状态 |
| channelRawBody | 渠道原始报文 JSON 字符串（供管理员查看凭证） |

错误：订单不存在（-1）、订单无支付渠道（-1）。

## 5. 实现要点

### 支付宝侧：AliPayService 新增 queryAndSyncStatus(orderNo)

- 复用 `queryOrder(orderNo)` 获取原始 body（null → 状态不确定 UNKNOWN，本地不动）。
- `ACQ.TRADE_NOT_EXIST` → 渠道无此交易，仅展示，**不关单**（与对账语义刻意区分）。
- `TRADE_SUCCESS/TRADE_FINISHED` → `paymentSuccessService.handlePaymentSuccess(...)`（幂等，firstSettled 时落支付流水 `createPaymentInfoForAliPay`）。
- 其余状态（WAIT_BUYER_PAY/TRADE_CLOSED 等）仅展示。

### 微信侧：复用 WxPayOrderFacade.queryPaymentStatus(orderNo)

- 已具备：成功同步、渠道无单保持本地状态不关单、幂等锁。
- 控制器将 WxPayStatusVO 映射为 ChannelOrderQueryVO（含查单后重查 pay_status）。

### 控制器：AdminOrderShipmentController

- 注入 `WxPayOrderFacade`、`AliPayService`。
- 按 `orderInfo.getPaymentType()` 路由；非微信/支付宝订单抛业务错误。

## 6. 前端（vue-admin + react-admin AdminOrders）

- 操作列新增"渠道查单"按钮（订单维度）。
- 点击调用 `shipmentApi.channelQuery(orderNo)`，结果弹窗展示：渠道状态、描述、本地状态 before→after、支付状态、是否同步；可展开查看渠道原始报文。
- 关闭弹窗后刷新订单列表。

## 7. 兼容性

- 纯新增端点与前端按钮，不改动既有接口行为。
- 不调用渠道关单/退款接口，无资金操作风险。

## 8. 验收标准

1. [x] `mvn compile` 通过（BUILD SUCCESS）。
2. [x] 微信订单：复用 `WxPayOrderFacade.queryPaymentStatus`，返回 trade_state + 本地状态前后对比，TRADE_SUCCESS 时本地订单推进（幂等，已有锁与事务保证）。
3. [x] 支付宝订单：新增 `AliPayService.queryAndSyncStatus`，TRADE_SUCCESS 时走 `handlePaymentSuccess`；渠道无单/未支付/结果不明确仅展示，不关单。
4. [x] vue-admin 与 react-admin 均出现"渠道查单"按钮并可展示结果与原始报文。
5. [x] 双端 `npm run build` 通过（react `✓ built in 3.70s`；vue `DONE Build complete`）。

## 9. 实现锚点

| 组件 | 文件 |
|---|---|
| 统一结果 VO | `payment-demo/src/main/java/cc/ivera/vo/ChannelOrderQueryVO.java`（新增） |
| 管理端端点 | `controller/AdminOrderShipmentController.java` `GET /api/admin/order/{orderNo}/channel-query` |
| 支付宝查单同步 | `service/AliPayService.java` + `service/impl/AliPayServiceImpl.java#queryAndSyncStatus`（新增） |
| 支付宝枚举 | `enums/alipay/AliPayTradeState.java`（补充 FINISHED/TRADE_FINISHED） |
| vue-admin | `payment-demo-vue-admin/src/api/shipment.js`、`src/views/AdminOrders.vue`（操作列按钮 + 结果弹窗） |
| react-admin | `payment-demo-react-admin/src/api/shipment.js`、`src/pages/AdminOrders.jsx`（操作列按钮 + 结果 Modal） |

## 10. Change Log

- 2026-09-06：规范创建，方案经用户确认（统一端点、同步成功状态、不自动关单）。
- 2026-09-06：实现落地。新增管理端渠道查单统一端点（按 payment_type 路由微信/支付宝）；支付宝侧新增 queryAndSyncStatus（成功同步、不关单），AliPayTradeState 补充 FINISHED；微信侧复用 queryPaymentStatus 并映射统一 VO；双端管理订单页新增"渠道查单"按钮与结果弹窗（含渠道原始报文）。mvn compile 与双端构建通过，规范移入 implemented。
