# ADMIN_ORDER_SEARCH_FILTERS_SPEC — 管理员订单查询增加时间范围/订单号/用户编号筛选

- 状态：`implemented`（2026-09-06：后端 `mvn compile` 通过；Vue Admin / React Admin `npm run build` 均通过）
- 日期：2026-09-06
- 问题分类：Public API 增量变更（既有端点新增可选 query 参数 + 前端调用变更），无工作流/状态规则/领域模型变更。
- 需求来源：用户要求"管理 UI 端应额外支持根据时间范围、订单号、用户编号进行订单查询"。

## 1. 背景与问题

管理员订单管理页（Vue Admin `AdminOrders.vue` / React Admin `AdminOrders.jsx`）目前仅支持按支付状态、订单生命周期、履约状态三个下拉条件筛选，无法按业务常用的订单号、用户编号、下单时间范围定位订单。

## 2. 目标

`GET /api/admin/order/all` 在既有 `payStatus/orderStatus/fulfillmentStatus` 之外，新增三个可选筛选维度：

1. **订单号** `orderNo`：字符串，对 `t_order_info.order_no` 做包含模糊匹配（LIKE %value%），空白忽略。
2. **用户编号** `userId`：对 `user_id` 精确匹配；非数字入参返回业务错误"用户编号格式不正确"，空白忽略。
3. **时间范围** `startTime` / `endTime`：按 `create_time` 过滤（`>= startTime`、`<= endTime`）。
   - 接受 `yyyy-MM-dd`（日期）与 `yyyy-MM-dd HH:mm:ss`（日期时间）两种格式。
   - 仅日期时：startTime 补 `00:00:00`，endTime 补 `23:59:59`（保证结束日全天覆盖）。
   - startTime 晚于 endTime 返回业务错误"开始时间不能晚于结束时间"。
   - 时间格式非法返回业务错误，提示合法格式。

## 3. API 契约与兼容性影响

| 端点 | 变更 |
|---|---|
| `GET /api/admin/order/all` | 新增 4 个**可选** query 参数：`orderNo`、`userId`、`startTime`、`endTime`。 |

- **向后兼容**：全部参数可选，不传时行为与现状完全一致（按 create_time 倒序返回全部订单）；旧前端/旧调用方零影响。
- 响应结构 `R<List<OrderDetailVO>>` 不变，无 DB schema 变更，无新增端点。
- 回滚方式：还原前端筛选控件与后端参数解析即可，无数据迁移。

## 4. 实现锚点

### 4.1 后端
- `controller/AdminOrderShipmentController.java` L57-69：`allOrders` 新增 `orderNo/userId/startTime/endTime` 四个 `@RequestParam(required=false)` 参数并透传。
- `service/CheckoutService.java` L12-14：`listAllOrders` 签名扩展为 7 个参数。
- `service/impl/CheckoutServiceImpl.java` L116-150：`listAllOrders` 增加 `like("order_no",…)`、`eq("user_id", Long.parseLong)`（非数字抛 BizException）、`ge/le("create_time",…)`（起止倒置抛 BizException）；私有方法 `parseFilterTime` 用 SimpleDateFormat（lenient=false）解析，日期型入参开始日补 `00:00:00`、结束日补 `23:59:59`。

### 4.2 前端（两套 Admin 均改）
- Vue Admin（element-ui 2.15）`payment-demo-vue-admin/src/views/AdminOrders.vue`：筛选条新增订单号 `el-input`、用户编号 `el-input`、`el-date-picker type="daterange"`（value-format `yyyy-MM-dd`）；data 增加 `orderFilter.orderNo/userId` 与 `dateRange`；`loadAllOrders` 透传 `orderNo/userId/startTime/endTime`。
- React Admin（antd 5）`payment-demo-react-admin/src/pages/AdminOrders.jsx`：筛选区新增订单号 `Input`、用户编号 `Input`、`DatePicker.RangePicker`（format `YYYY-MM-DD`，onChange 得 dayjs 对象，查询时 format 为 `YYYY-MM-DD`）；新增 `orderNo/userId/dateRange` 三个 state；`loadAllOrders` 透传同上。

## 5. 验收标准

1. [x] 后端 `mvn compile` 通过；新参数全部可选，不传时 `/api/admin/order/all` 行为与变更前一致（仅新增 if 分支，无原条件改动）。
2. [x] `orderNo` 模糊匹配：`q.like("order_no", orderNo.trim())`，空白忽略。
3. [x] `userId` 精确匹配：`Long.parseLong` 后 `eq("user_id",…)`；非数字抛 BizException"用户编号格式不正确"，经 GlobalExceptionHandler 返回业务错误。
4. [x] `startTime/endTime` 按 create_time 闭区间过滤（ge/le）；传 yyyy-MM-dd 时结束日补 23:59:59 全天覆盖；非法格式抛"格式不正确"，起止倒置抛"开始时间不能晚于结束时间"。
5. [x] Vue Admin 与 React Admin 订单页均新增订单号输入框、用户编号输入框、日期范围选择器，点"查询"后参数正确透传。
6. [x] Vue Admin `npm run build` DONE；React Admin `npm run build` ✓ built（chunk 体积告警为既有项）。

> 备注：当前工作副本 `payment-demo/src/test` 测试源码缺失（target 中仅存历史编译产物），无法运行单测；后端以 `mvn compile` 编译验证，逻辑为纯增量查询条件。
