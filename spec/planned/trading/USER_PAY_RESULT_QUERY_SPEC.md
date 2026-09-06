# 用户端支付结果主动查单 SPEC

> 状态（status）：planned
> 领域（feature-domain）：trading
> 影响范围：backend | frontend（payment-demo-react / payment-demo-vue 商城双端）
> 创建日期：2026-09-06
> 问题分类：Design change（新增用户侧 Public API + 商城前端查单行为升级）

## 1. 背景

用户反馈：商城用户端支付（微信扫码）后"没有能力查询是否支付成功"。

现状：OrdersV2.jsx / Orders.vue 微信二维码弹窗已有「我已支付，查询支付结果」按钮，但仅读**本地库** payStatus（`GET /api/checkout/orders/{orderNo}`，与自动轮询同路径）。渠道回调延迟或丢失时本地状态停留在 UNPAID，用户反复点击永远得到"暂未查到支付结果"。

管理端已有主动渠道查单并同步本地状态的能力（`AdminOrderShipmentController#channelQuery` → 微信 `queryPaymentStatus` / 支付宝 `queryAndSyncStatus`，已支付成功则推进本地订单）。用户端缺同类入口。

## 2. 方案

### 2.1 后端：用户侧主动查单接口（归属校验 + 渠道查单 + 本地同步）

- 新增 `GET /api/checkout/orders/{orderNo}/pay-query`（登录用户）：
  1. `checkoutService.getOrder(userId, orderNo)` 归属校验（非本人订单 404/业务错误，与既有 `get` 同路径）；
  2. 订单已 PAID → 直接返回当前状态（不调渠道，省额度）；
  3. 按支付方式分支：WXPAY → `wxPayOrderFacade.queryPaymentStatus(orderNo)`；ALIPAY → `aliPayService.queryAndSyncStatus(orderNo)`。两者均已内置"渠道确认成功→同步本地订单"（微信路径本迭代已统一走 `handlePaymentSuccess`，同步回写支付单）；
  4. 渠道查单后重读订单，返回强类型 `PayQueryVO`：orderNo / payStatus / orderStatus / channelCode / channelTradeState / channelTradeStateDesc / synced。
- 安全边界：登录态 + 订单归属校验；无管理员数据泄露面（不返回渠道原始报文）。
- 契约遵循：统一 R 包装、code 契约与 GlobalExceptionHandler 既有错误链路；强类型 VO（非 Map）。

### 2.2 商城双端：查单按钮改调新接口

- `api/checkout.js`（React/Vue 同步）新增 `payQuery(orderNo)`。
- OrdersV2.jsx / Orders.vue 弹窗按钮 `queryPayResult` 改调 `payQuery`：`payStatus === 'PAID'` → 提示"支付成功" + 关闭二维码弹窗 + 刷新订单列表；否则按渠道返回的 `channelTradeStateDesc` 给出友好提示。React 弹窗既有 3s 本地轮询保留。

## 3. 兼容性影响与回滚

- **Public API**：新增用户侧只读接口（增量），无既有接口结构变更；前端仅改按钮的数据源。
- **DB**：无 schema 变更；渠道确认成功后的本地同步复用既有 `handlePaymentSuccess` 状态机（幂等）。
- **回滚**：git revert；旧按钮回退为本地状态查询，无残留影响。

## 4. 验收标准

- [ ] 后端 `GET /api/checkout/orders/{orderNo}/pay-query`：归属校验、已支付短路、按渠道分支查单并同步本地状态、返回 PayQueryVO（锚点：`CheckoutController`、`cc/ivera/vo/PayQueryVO.java`）。
- [ ] React 商城二维码弹窗按钮改调 `payQuery`，支付成功提示并关窗刷新（锚点：`pages/OrdersV2.jsx`）。
- [ ] Vue 商城同按钮对等改造（锚点：`views/Orders.vue`、`api/checkout.js`）。
- [ ] `mvn compile` 通过；react vite build `✓ built`；vue-cli `DONE Build complete`（串行）。
- [ ] 运行时验证（待后端重启新代码）：未支付订单点击按钮 → 渠道查单返回 NOTPAY 类提示；渠道已支付 → 按钮点击后本地 PAID 并提示"支付成功"。

## 5. 实施锚点

- `payment-demo/src/main/java/cc/ivera/controller/CheckoutController.java`
- `payment-demo/src/main/java/cc/ivera/vo/PayQueryVO.java`（新增）
- `payment-demo-react/src/api/checkout.js`、`payment-demo-react/src/pages/OrdersV2.jsx`
- `payment-demo-vue/src/api/checkout.js`、`payment-demo-vue/src/views/Orders.vue`

## 6. Change Log

| 日期 | 状态 | 说明 |
|---|---|---|
| 2026-09-06 | planned | 初版：用户端支付结果主动查单（归属校验 + 渠道查单同步 + 强类型 VO + 商城双端按钮升级） |
| 2026-09-06 | planned | 实现落盘：PayQueryVO + `payQuery` 接口（CheckoutController）+ 双端 `checkoutApi.payQuery` + 弹窗按钮数据源切换；compile/双 build 通过，运行时验证待后端重启（与 PAYMENT_ORDER_CHANNEL_ATTEMPTS_SPEC 同批重启） |
