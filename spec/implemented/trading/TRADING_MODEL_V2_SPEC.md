# TRADING_MODEL_V2_SPEC — 交易系统六域模型改造

- 状态：`implemented`（2026-09-05 迁移：单测/特征化 85+ 项全绿、React/Vue build 通过、DM8 运行库迁移与全链路冒烟实测完成，锚点见 §5/§6）
  - 遗留待真实渠道验证项：微信/支付宝「渠道存在 NOTPAY 单」的主动关单路径、渠道退款成功 settle 结转路径（沙箱假交易不可模拟，单测已覆盖逻辑）。
- 日期：2026-09-05
- 问题分类：Design change（工作流 / 状态机 / 领域模型 / DB schema / API 契约变更）
- 需求来源：《订单、支付、退款设计文档》（用户提供的 2648 行设计稿）
- 计划文档：`.trae/documents/trading-model-v2-refactor.md`

## 1. 目标

从"支付后异步扣库存、退款无冻结防线"的旧模型升级为 6 域对象模型 + 物流履约域：

```
业务订单 Order (t_order_info)
    ├── 1:N 订单明细 OrderItem (t_order_item)
    ├── 1:N 支付单 PaymentOrder (t_payment_order)          [新增]
    ├── 1:N 退款单 RefundOrder (t_refund_order)            [由 t_refund_apply 演进]
    │       └── 1:N 退款明细 RefundItem (t_refund_item)    [由 t_refund_apply_item 演进]
    ├── 1:N 库存预占 InventoryReservation (t_inventory_reservation)  [新增]
    ├── 1:N 库存流水 InventoryTransaction (t_inventory_transaction)  [新增]
    └── 1:N 物流单 OrderShipment (t_order_shipment)        [新增，模拟物流商对接]
```

核心机制：
1. 下单原子预占库存（available_stock → locked_stock），支付成功提交（locked→COMMITTED），超时关单释放（LOCKED→RELEASED 归还可用库存）。
2. 退款三层冻结防线：Order.refund_frozen_amount / OrderItem.refund_frozen_qty+amount / PaymentOrder.refund_frozen_amount，全部原子 UPDATE ... WHERE 余额足够（affectedRows=1）。
3. 退款金额服务端计算（§8 尾差规则"最后一件吃尾差"），客户端只传 orderItemId + refundQty。
4. 按退款类型决定是否补库存：CANCEL_BEFORE_SHIP=补；RETURN_AND_REFUND=签收质检后补；REFUND_ONLY/PRICE_ADJUSTMENT/DUPLICATE_PAYMENT/LATE_PAYMENT=不补。
5. 异常支付（重复/晚到）自动原路退款，不占售后额度、不占 refunded_qty、不补库存。
6. 订单四维状态：order_status(WAIT_PAY/ACTIVE/CLOSED/COMPLETED)、pay_status(UNPAID/PAID)、fulfillment_status(WAIT_SHIP/SHIPPED/RECEIVED/CANCELLED)、refund_status(NONE/REFUNDING/PARTIAL_REFUNDED/FULL_REFUNDED)。
7. 履约与退款联动：CANCEL_BEFORE_SHIP 仅 WAIT_SHIP；RETURN_AND_REFUND 需 RECEIVED；REFUND_ONLY 需 SHIPPED/RECEIVED。
8. 物流模拟对接：LogisticsProvider 接口 + MockLogisticsProvider（模拟运单号/状态），MockLogisticsSimulationJob 定时推进 SHIPPED→IN_TRANSIT→DELIVERED，用户确认收货 → RECEIVED。

## 2. 数据库变更

### 2.1 全量脚本 payment_demo.sql（新装库）
- t_product：stock→available_stock，新增 locked_stock。
- t_order_info：order_status→legacy_status；新增四维状态列 + paid_amount/refund_frozen_amount/refunded_amount/expire_time/paid_time/receiver_name/receiver_phone/receiver_address。
- t_order_item：refunded_quantity→refunded_qty；新增 deal_unit_amount/original_total_amount/discount_amount/pay_amount/refund_frozen_qty/refund_frozen_amount/refunded_amount/restocked_qty。
- 新表：t_payment_order、t_inventory_reservation、t_inventory_transaction、t_order_shipment。
- t_refund_apply→t_refund_order（新增 refund_type/payment_no/status/success_time，apply_status→legacy_apply_status）；t_refund_apply_item→t_refund_item（新增 restock_qty/status，refund_quantity→refund_qty，stock_returned→legacy_stock_returned）。

### 2.2 增量迁移脚本 upgrade_trading_model_v2.sql（旧库，幂等守卫）
步骤顺序与回填映射见计划文档 Step 1b；关键点：
- 旧 order_status 值映射：NOTPAY→WAIT_PAY/UNPAID；SUCCESS→ACTIVE/PAID；CLOSED/CANCEL→CLOSED/UNPAID；REFUND_PROCESSING→REFUNDING；PARTIAL_REFUND→PARTIAL_REFUNDED；REFUND_SUCCESS→FULL_REFUNDED；OVER_SOLD_CLOSED→CLOSED/PAID/FULL_REFUNDED。
  **注意**：V1 代码以 `OrderStatus.getType()` 中文值落库，legacy_status 实际取值为 '未支付'/'支付成功'/'超时已关闭'/'用户已取消'/'超卖关闭'/'退款中'/'部分退款'/'已退款' 等，脚本匹配条件必须使用中文值（'退款异常' 落入 ELSE 分支，痕迹保留在 legacy_status 审计）。
- 历史支付回填 t_payment_order（payment_no='PMO'||order_no||'-'||payment_type）。
- 历史订单回填审计 reservation/transaction（PAID→COMMITTED；CLOSED→RELEASED）。
- 历史退款状态回填 + refunded/frozen 结转。
- 不回填历史物流单；locked_stock 从 0 起算。

## 3. API 契约与兼容性影响

| 契约 | 变更 | 兼容性 |
|---|---|---|
| GET 订单详情/列表 | 新增 payStatus/fulfillmentStatus/refundStatus/paidAmount/refundedAmount/refundFrozenAmount/expireTime/paidTime/shipment 字段 | 增量；orderStatus 保留旧值映射（WAIT_PAY→NOTPAY、ACTIVE→SUCCESS、CLOSED→CLOSED、refund_status 非 NONE 映射旧退款值） |
| OrderItem JSON | refundedQuantity → refundedQty | **破坏性**；前端同步更新 |
| Product JSON | stock 键名不变（语义=可用库存）；新增 lockedStock | 增量 |
| POST /api/refund-apply | 请求只传 orderItemId+quantity（可选 refundType，金额服务端算）；响应新增 refundAmount/refundType/status | 增量 + 行为变更（金额不再信任前端） |
| POST /api/checkout/orders | 请求新增可选 receiverName/receiverPhone/receiverAddress（物流模拟，缺省模拟值） | 增量 |
| POST /api/order/{orderNo}/cancel | 新增：已付款未发货取消 | 新端点 |
| POST /api/order/{orderNo}/confirm-receipt、GET /api/order/{orderNo}/shipment | 新增：确认收货/物流查询 | 新端点 |
| POST /api/admin/order/{orderNo}/ship、GET /api/admin/order/wait-ship | 新增：模拟物流发货/待发货订单列表 | 新端点 |
| POST /api/admin/refund/{refundNo}/reject、/confirm-return、/price-adjustment | 新增：拒绝/退货签收/差价退款 | 新端点 |
| 管理员调库存 | 行为不变（调 available_stock） | 兼容 |

迁移/回滚说明：升级脚本保留 legacy_status/legacy_apply_status 旧列，代码回滚旧分支仍可读旧列运行；新表独立，回滚不影响旧表结构（新增列保留为空默认值）。

## 4. 后端模块设计

- 枚举：OrderLifecycleStatus / PayStatus / FulfillmentStatus / OrderRefundStatus / RefundOrderStatus / RefundType / ReservationStatus / InventoryBizType / ShipmentStatus（旧 OrderStatus/RefundStatus 保留用于 legacy 映射与渠道侧状态）。
  **落库值口径**：V2 状态枚举 `getType()` 一律返回英文标识（与 DDL 默认值/迁移脚本一致，如 WAIT_PAY/UNPAID/APPLYING/SHIPPED），中文仅用于前端展示映射；V1 `OrderStatus` 中文值仅用于 legacy_status 与旧 API 兼容映射（实现锚点：`CheckoutServiceImpl.legacyViewStatus`）。
- 库存域：InventoryService（reserve/commit/release/restock），全部本地事务同步，不走 MQ；MQ deduct/replenish 链路移除。
- 支付域：PaymentOrder 于支付发起时创建；PaymentSuccessService 统一处理 notify/主动查单：CAS WAIT_PAY→ACTIVE + PaymentOrder SUCCESS + commitReservation + 关闭其他支付单；重复支付/晚到支付 → ExceptionRefundService 自动冲正。
- 退款域：RefundOrderService（create/cancel/reject/accept/confirmReturn/settle），RefundPolicy 纯函数计算额度与尾差；渠道退款成功后冻结→已退结转。
- 物流域：ShipmentService（ship/confirmReceipt/getByOrderNo）+ MockLogisticsSimulationJob。

## 5. 测试锚点

- 基线：PublicApiCharacterizationTest、InfrastructureBehaviorCharacterizationTest（改造前后均须全绿）。
- 新增（全部落地，路径 payment-demo/src/test/java/...）：
  - [x] RefundPolicyTest — 尾差/额度不变量
  - [x] RefundTypeRestockPolicyTest — 补库存矩阵 + 履约校验
  - [x] InventoryServiceTest — 预占/提交/释放/回补
  - [x] RefundOrderServiceTest — 冻结/释放/结转/重复明细拒绝
  - [x] PaymentSuccessServiceTest — CAS 竞态/重复/晚到
  - [x] ShipmentServiceTest — 发货分界/模拟推进/确认收货
  - [x] CheckoutServiceV2Test — 下单预占/四维状态显式落库/V1 兼容映射
  - [x] TradingModelV2CharacterizationTest — 新行为特征化

### 5.1 前端锚点（React/Vue 对称，均 build 通过）

- API 层：`api/shipment.js`（新建：getShipment/confirmReceipt/cancelPaidOrder/waitShipList/shipOrder）；`api/refundApply.js` 增 confirmReturn/retry/priceAdjustment。
- 额度工具：`utils/refundQuota.js` 重写为 V2 明细字段口径（quantity - refundedQty - refundFrozenQty；金额预估对齐 RefundPolicy 尾差规则），不再依赖退款申请列表聚合；编辑场景 `availableRefundQuantityExcluding`。
- 状态映射：`utils/statusLabels.js`（新建）承载四维状态/退款类型/退款单状态/物流状态中文映射与 `statusTags` 兼容回退。
- 订单页（`OrdersV2.jsx` / `Orders.vue`）：四维状态徽标、物流详情时间线弹窗、DELIVERED 后「确认收货」、PAID+WAIT_SHIP「取消订单」、分项退款弹窗按履约状态限定 refundType 并展示服务端核算金额、微信支付扫码弹窗（React 用 `qrcode.react` 的 QRCodeSVG 渲染、弹窗期间每 3s 轮询订单状态支付成功自动关窗；Vue 用 `vue-qriously` canvas 渲染）。
- 退款记录页（`RefundApplications.jsx` / `.vue`）：applyStatus→status、refundQuantity→refundQty、新增 refundType 列；仅 APPLYING 可编辑/撤销。
- 结算页（`Cart.jsx` / `Cart.vue`）：可选收货人姓名/电话/地址表单，缺省走后端模拟值。
- 管理控制台（`AdminConsole.jsx` / `AdminConsole.vue`）：新增「订单发货」Tab（wait-ship 列表 + 模拟发货）、退款受理 Tab 适配 V2 状态并支持退货签收确认/失败重试、差价退款弹窗、库存 Tab 可用/锁定双列。
- 后端锚点补充：`CheckoutRequest` 增收货人三字段（`CheckoutServiceImpl.trimOrDefault` 缺省模拟值）；`AdminOrderShipmentController.waitShipList`（GET /api/admin/order/wait-ship）。

## 6. 验收标准

1. [x] 单测/特征化全部通过（PublicApiCharacterizationTest、InfrastructureBehaviorCharacterizationTest 及 V2 新增单测，11 组全绿）。
2. [x] React/Vue `npm run build` 通过。
3. [x] 运行库迁移（2026-09-05 实测）：192.168.1.200 DM8 通过 JDBC 执行 `upgrade_trading_model_v2.sql` 两次（幂等，42 块全 OK）；6 张 V2 表、`t_order_info` V2 列、`t_product` available/locked 双列全部到位；回填标记唯一；历史数据（4 商品）保留。执行前修正了脚本 7.1/7.2 段 `legacy_status` 匹配值（英文枚举名 → V1 实际落库中文值，如 '未支付'/'超卖关闭'）。
4. [x] 全链路冒烟（2026-09-05 实测，后端 8081 + DM8/Redis/RabbitMQ 运行库）：
   - 下单预占：available 100→97 / locked 0→3，reservation=LOCKED，ORDER_RESERVE 流水，四维状态 WAIT_PAY/UNPAID/WAIT_SHIP/NONE，收货人/15min 过期落库；
   - 支付（模拟微信 notify 合法签名回调）：订单 ACTIVE/PAID + paid_amount，PaymentOrder=SUCCESS，reservation=COMMITTED，ORDER_COMMIT 流水；
   - 管理员发货：wait-ship 列表 → 模拟运单 MOCK*，SHIPPED；
   - 模拟物流推进（定时任务 CAS）：SHIPPED→IN_TRANSIT→DELIVERED 时间线完整；未送达时确认收货被拒（退款防线）；
   - 确认收货：DELIVERED→RECEIVED；
   - 退货退款（RETURN_AND_REFUND）：服务端计算金额（快照价），三层冻结（order 1 分/item 1 件/pmo 1 分），受理→签收确认→补库存 1 件 + REFUND_RESTOCK 流水；渠道退款失败（沙箱无真实交易 RESOURCE_NOT_EXISTS）→ 退款单 FAILED、冻结保留可 retry —— 库存回补与渠道退款解耦验证通过；
   - 未发货取消（CANCEL_BEFORE_SHIP）：服务端全额计算（尾差），受理→补库存 2 件 + 流水；渠道失败同上；
   - 渠道退款成功结转（settle）路径由单测覆盖（真实渠道需真实交易，沙箱不可模拟）；
   - 超时关单（2026-09-05 实测）：修复前，从未发起支付的超时订单被微信 V3 查单 HTTP 404 `ORDER_NOT_EXIST` 包成 BizException，`TimeoutOrderCloseScheduler` catch 后无限重试、库存预占无法释放；修复后，渠道明确无此交易（= 从未支付）时跳过渠道关单、直接走本地 V2 关单链路（CAS NOTPAY→CLOSED：关闭活跃 PaymentOrder + releaseReservation + ORDER_RELEASE），网络/5xx 等不确定错误仍抛异常等待下轮重试。修复实例重启后调度器自动关闭历史超时单 ORD202609051544377330（legacy=超时已关闭 / order_status=CLOSED，product1 库存 99/0 账实相符）；支付宝侧对称处理 ACQ.TRADE_NOT_EXIST（单测覆盖；"渠道存在 NOTPAY 单"的真实渠道主动关单路径待开发人员接入真实支付环境验证）；
   - 发货→物流→收货复测（修复实例 8081，新订单 ORD202609051627179362，模拟微信 notify 回调支付）：wait-ship → 模拟运单 MOCK17885974383030770（SHIPPED 16:37:18）→ 定时任务 CAS 推进 IN_TRANSIT 16:37:24 → DELIVERED 16:37:34 → 用户确认收货 RECEIVED 16:37:38，时间线四段完整；重复发货被拒（"订单已确认收货"）；库存预占提交后 99/0 账实相符。
5. [x] 冒烟暴露并已修复的运行期缺陷（实现锚点）：
   - [x] 启动循环依赖：aliPayServiceImpl→paymentSuccessServiceImpl→exceptionRefundServiceImpl→aliPayServiceImpl，`ExceptionRefundServiceImpl` 构造器 `@Lazy AliPayService` 打断；
   - [x] OrderItem 成交快照金额未落库（dealUnitAmount/payAmount 等为 null，退款计算依赖）：`CheckoutServiceImpl` 与 `OrderInfoServiceImpl`（快速购买）两处下单补齐；快速购买路径同步补四维状态显式落库与收货人模拟值；
   - [x] 模拟物流推进无效列名：`ShipmentServiceImpl.advanceTransition` 查询列传驼峰（shippedTime）而 DB 为下划线（shipped_time），DM8 报"无效的列名"导致物流永不推进；已改下划线（casShipmentStatus 的 timeColumn 逻辑键不受影响）；
   - [x] `/api/order/**` 未纳入鉴权（AuthInterceptor.isPublic 默认放行，不设置 AuthContext）：已加入需登录名单；`OrderShipmentController` 物流查询/确认收货增加订单归属校验（requireOwnership，防水平越权）；
   - [x] 超时关单渠道查无此单死循环：`WxPayOrderService.queryOrderBodyIfExists` 区分微信 V3 404 `ORDER_NOT_EXIST`（渠道无交易=从未支付）→ `closeLocalOrderWhenChannelAbsent` 走本地 V2 关单链路，`queryPaymentStatus` 按 NOTPAY 展示、不触发关单；渠道关单接口 `closeOrder` 容忍 `ORDER_NOT_EXIST`；`AliPayServiceImpl.queryOrder/checkOrderStatus` 对称处理 `ACQ.TRADE_NOT_EXIST`；聚焦单测 `WxPayOrderReconcileTest`（4 例），回归 85 项全绿。
   - [x] React 端微信 code_url 不渲染二维码（2026-09-05 实测修复）：`OrdersV2.jsx` 调 `/api/checkout/orders/{orderNo}/wxpay` 拿到 `data.codeUrl` 后仅 `message.info` 弹文本、未使用已声明的 `qrcode.react` 依赖；改为 antd Modal 弹窗 + `<QRCodeSVG value={codeUrl} size={300}/>` 渲染二维码，弹窗期间每 3s 轮询订单列表、检测到 PAID 自动关窗并刷新；Vue 端 `Orders.vue` 的 `qriously` 扫码弹窗接线完整（插件注册、value watcher 重绘均验证）无需改动。React `npm run build` 通过；浏览器端到端实测（订单 ORD202609051646273024）：弹窗出现、300×300 二维码 SVG 正常渲染、`weixin://wxpay/bizpayurl?pr=...` 文本显示正常。
