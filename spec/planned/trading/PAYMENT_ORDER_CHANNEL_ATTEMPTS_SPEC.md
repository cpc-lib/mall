# 支付单渠道尝试记录与渠道查单同步收口 SPEC

> 状态（status）：planned
> 领域（feature-domain）：trading
> 影响范围：backend | admin-frontend（react-admin / vue-admin 双端）
> 创建日期：2026-09-06
> 问题分类：Design change（支付单生命周期规则 + 管理端新增只读接口 + 双端 UI 展示）

## 1. 背景

用户反馈：同一本地订单可能触发多次三方支付（换渠道重发起、重复点支付），后端应该有"第三方下单的记录表"，并对接渠道端查单获取支付状态，进而处理本地订单支付状态。

现状盘点（大部分能力已存在）：

| 能力 | 现状 | 锚点 |
|---|---|---|
| 第三方下单记录表 | 已有 `t_payment_order` 支付单表（订单 1:N 渠道支付尝试，含渠道/渠道侧交易号/金额/状态/时间） | `payment_demo.sql` L561-L606、`PaymentOrder` |
| 渠道查单 | 微信 V3 查单 + 支付宝 `trade.query` 均已实现 | `WxPayOrderService.queryPaymentStatus`、`AliPayServiceImpl.queryAndSyncStatus` |
| 渠道状态→本地同步 | 异步回调 notify / 管理端渠道查单 / 定时对账三条路 | `PaymentSuccessService.handlePaymentSuccess`、`TimeoutOrderCloseScheduler.checkOrderStatus` |
| 管理端渠道查单 UI | 双 admin 端「渠道查单」按钮 + 弹窗（实时渠道状态 + 原始报文） | `AdminOrderShipmentController#channelQuery`、`AdminOrders.jsx/.vue` |

## 2. 缺口（本次要修复）

- **缺口 A：跨渠道重发起支付，支付单归属错乱。**
  `PaymentOrderServiceImpl.startPayment` 复用活跃支付单时不匹配渠道：先点微信（生成 WXPAY 支付单 PAYING）再点支付宝，支付宝这次三方下单会记在同一行 WXPAY 支付单上（channel 不变、code_url 被覆盖），渠道尝试记录失真。
- **缺口 B：微信查单同步 SUCCESS 绕过支付单状态机。**
  `WxPayOrderService.doSyncOrderStatusFromWxQuery` 查到已支付只推进订单状态，支付单不回写 SUCCESS/渠道交易号/实付金额/支付时间（支付宝路径走 `handlePaymentSuccess`，两渠道不一致）。异常冲正资金防线按支付单 `SUCCESS` + `paid_amount` 封顶执行（`ExceptionRefundServiceImpl`），支付单未落成功会导致该笔资金无法冲正/退款封顶失准。
- **缺口 C：管理端无支付尝试记录展示。**
  渠道查单弹窗只有实时渠道状态，看不到 `t_payment_order` 历史尝试列表（渠道、发起时间、渠道交易号、状态、金额）。
- **缺口 D（随 A 引入必须一并处理）：渠道盲查的幂等/冲正锚点风险。**
  订单 SUCCESS 后收口其它渠道活跃支付单时，`handlePaymentSuccess` 若继续按"订单号盲查"定位支付单，晚到支付的另一渠道通知会把冲正锚到已成交渠道的支付单上（退错钱）。渠道感知是收口的前提。

## 3. 方案设计

### 3.1 startPayment 渠道感知（修 A）

- 复用规则改为：存在**同渠道**活跃支付单（CREATED/PAYING）→ 复用；否则新建本渠道支付单行。
- 跨渠道**不提前关闭**旧渠道活跃单：用户可能已扫旧渠道二维码，支付回调需能按渠道正确落行；订单终态（SUCCESS/CLOSED/CANCEL）统一收口。
- 同渠道重复发起（支付宝每次 pageExecute 重新预下单、微信 JSAPI 重新预支付）：`out_trade_no` 相同，渠道侧视为同一笔交易，现状保留复用同一支付单行，spec 明示该现状。

### 3.2 handlePaymentSuccess 渠道感知定位 + 成交收口（修 B/D）

定位顺序（均按 `orderNo + channel`）：
1. 活跃支付单（CREATED/PAYING）→ `markSuccess` 后按订单状态分派；
2. 无活跃单但本渠道已有 SUCCESS 单：同渠道交易号 → 幂等返回；不同交易号 → 兼容补建本渠道 PAYING 单 → `markSuccess` → `duplicatePayment(新行)`（冲正锚点=本次支付，不再误退原成交单）；
3. 均无 → 兼容补建本渠道 PAYING 单后走正常分派。

订单首次成交（NOTPAY→SUCCESS CAS 成功）后，新增收口：关闭该订单**其它渠道**活跃支付单（新 mapper 方法 `closeActiveByOrderNoExceptPaymentNo(orderNo, paymentNo)`）。之后这些渠道晚到支付由第 2 条路径兜底冲正。

### 3.3 微信查单同步统一走 handlePaymentSuccess（修 B）

`doSyncOrderStatusFromWxQuery` SUCCESS 分支改为：金额/商户校验（保留 `validateWxPayOrderNotify`）→ `handlePaymentSuccess(orderNo, WXPAY, transaction_id, payer_total)` → `firstSettled` 时写支付流水。与支付宝 `queryAndSyncStatus`/`checkOrderStatus` 对齐，支付单回写（SUCCESS/渠道交易号/实付金额/支付时间）+ 其它渠道收口自动生效。

### 3.4 管理端支付尝试记录 API + 双端 UI（修 C）

- 新增只读接口：`GET /api/admin/order/{orderNo}/payment-orders` → `R<List<PaymentOrder>>`（按 id 升序；404 语义：订单不存在报业务错误）。鉴权沿用 `/api/admin/**` 既有拦截，无新权限点。
- 双 admin 端「渠道查单」弹窗内嵌「支付尝试记录」表格：paymentNo、渠道、状态、渠道交易号、请求/实付金额、发起时间、支付时间。打开弹窗时加载。

## 4. 兼容性影响与回滚

- **Public API**：新增管理端只读接口（增量）；无既有接口请求/响应结构变更。
- **DB**：无 schema 变更（沿用现有 `t_payment_order` 与函数唯一索引 `(channel, channel_order_no)`；NULL 渠道单号行不参与唯一判定，多渠道各一行活跃单不冲突）。
- **行为变化**：跨渠道重发起支付由"复用同一行"变为"各渠道一行"（记录更准确）；微信查单同步将回写支付单成功态（补齐本应落库的字段）。
- **回滚**：git revert 即可；新增支付单行不会回滚删除，但不影响资金不变量（每订单仍仅一笔 SUCCESS 成交支付，冲正防线不变）。

## 5. 验收标准

- [x] `startPayment` 同渠道复用、跨渠道新建：微信发起后再发起支付宝，产生两行支付单且 channel 归属正确（代码锚点：`PaymentOrderServiceImpl.startPayment`）。
- [x] `handlePaymentSuccess` 按 orderNo+channel 定位；不同渠道晚到支付冲正锚定本次支付单行，不误退原成交单（代码锚点：`PaymentSuccessServiceImpl`）。
- [x] 订单首次成交后其它渠道活跃支付单被置 CLOSED（代码锚点：`PaymentOrderMapper.closeActiveByOrderNoExceptPaymentNo` + XML）。
- [x] 微信查单同步 SUCCESS 走 `handlePaymentSuccess`：支付单回写渠道交易号/实付金额/支付时间（代码锚点：`WxPayOrderService.doSyncOrderStatusFromWxQuery`）。
- [x] 管理端 API 可按订单号返回支付尝试列表（代码锚点：`AdminOrderShipmentController`）。
- [x] react-admin / vue-admin 渠道查单弹窗展示支付尝试记录表，双端构建通过（vite `✓ built in 3.69s` / vue-cli `DONE Build complete`，2026-09-06）。
- [ ] `mvn compile` 通过（✓ 2026-09-06）；运行时验证待后端重启（工作区无 `src/test`，特征测试恢复后补跑）。

## 6. 实施锚点

- `payment-demo/src/main/java/cc/ivera/service/impl/PaymentOrderServiceImpl.java`
- `payment-demo/src/main/java/cc/ivera/service/impl/PaymentSuccessServiceImpl.java`
- `payment-demo/src/main/java/cc/ivera/service/impl/wxpay/WxPayOrderService.java`
- `payment-demo/src/main/java/cc/ivera/mapper/PaymentOrderMapper.java` + `resources/mapper/PaymentOrderMapper.xml`
- `payment-demo/src/main/java/cc/ivera/controller/AdminOrderShipmentController.java`
- `payment-demo-react-admin/src/api/shipment.js`、`src/pages/AdminOrders.jsx`
- `payment-demo-vue-admin/src/api/shipment.js`、`src/views/AdminOrders.vue`

## 7. Change Log

| 日期 | 状态 | 说明 |
|---|---|---|
| 2026-09-06 | planned | 初版：支付单渠道尝试记录与渠道查单同步收口方案（缺口 A/B/C/D + 管理端 API/UI） |
