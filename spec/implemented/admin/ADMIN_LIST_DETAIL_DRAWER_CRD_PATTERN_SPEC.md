# 管理端「左菜单 + 右列表 + 抽屉详情/编辑/新增」统一交互模式 Spec

- Date: 2026-09-06
- Status: planned
- Domain: admin（frontend，payment-demo-react-admin / payment-demo-vue-admin）
- Class: Design change（管理端交互模式统一；后端 API 契约不变）

## 1. 背景与问题

管理后台已完成「左侧功能导航 + 右侧内容」的布局隔离（AdminLayout 的 Sider + Content），但各页面的详情/编辑/新增交互不统一：

- 商品「新增商品」仍为居中 Modal；商品详情已是抽屉
- 支付配置（渠道/应用）新增与编辑为 Modal
- 订单管理无详情视图（仅行内差价退款 Modal + Popconfirm）
- 退款受理无详情视图（仅行内按钮）
- 密码重置申请的受理/拒绝为多个 Modal
- 用户重置密码为独立 Modal（用户详情已是抽屉）
- 对账账单流水/差异查看为大宽 Modal

## 2. 目标模式（验收标准）

统一为：**左侧菜单（已有）→ 右侧列表 → 抽屉承载详情/编辑/新增**。

| 页面 | 列表 | 抽屉内容 |
|------|------|----------|
| 商品库存 | 商品表 | 详情抽屉（已有：信息+库存调整+上下架+库存日志）；新增商品由 Modal 改抽屉 |
| 用户列表 | 用户表 | 详情抽屉（已有：信息+在线状态）；重置密码操作并入抽屉底部 |
| 支付配置 | 渠道/应用 Tab 表 | 渠道、应用的新增/编辑由 Modal 改抽屉（表单同字段） |
| 订单管理 | 订单表 | 新增订单详情抽屉：订单/支付/履约状态、收货信息、明细、退款单；差价退款表单置于抽屉内 |
| 退款受理 | 退款表 | 新增退款详情抽屉：申请信息+明细+快照金额；受理/拒绝/签收/重试操作置于抽屉底部 |
| 密码重置申请 | 申请表 | 新增申请详情抽屉：用户信息+申请说明；受理/拒绝（拒绝原因输入）置于抽屉底部 |
| 对账管理 | 批次表 | 账单流水+差异查看由 Modal 改抽屉；差异「标记处理」保留小 Modal（备注必填，属动作弹窗） |

### 交互规则

1. 抽屉为右侧滑出（antd `Drawer placement="right"`；Element `el-drawer direction="rtl"`），宽度 560–720px；流水/明细等宽表格抽屉可用 900–1000px。
2. 抽屉采用「受控 visible + 主键入参 + 打开时加载/关闭时清理」固定模式，重复打开不残留上一次数据。
3. 详情抽屉底部固定操作区（主操作实心主色按钮，次操作/危险操作对应样式）；列表行保留快捷操作入口（点击行或「详情」按钮打开抽屉）。
4. 纯确认类动作（Popconfirm 删除/上下架、标记已处理备注）保持轻量弹窗，不强制抽屉化。
5. 双框架（React antd / Vue Element）页面功能与交互对等。

## 3. 兼容性

- 纯前端交互改造，路由、API、请求/响应结构均不变；StockExcelEditor 独立新页签全屏编辑器不在本次范围。
- 回滚 = git 还原本次提交。

## 4. 实施锚点

- React：`payment-demo-react-admin/src/pages/` AdminProducts.jsx、UserList.jsx、PaymentConfig.jsx、AdminOrders.jsx、AdminRefunds.jsx、AdminResetRequests.jsx、Reconciliation.jsx
- Vue：`payment-demo-vue-admin/src/views/` 同名 7 个 .vue 文件
- 共用样式：`src/assets/css/admin.css`（.adm-card 已存在，抽屉细节沿用组件库默认）

## 5. 验收

- [x] React 管理端：7 个页面详情/新增/编辑均为抽屉；`npm run build` 通过（✓ built in 3.91s）
- [x] Vue 管理端：对等改造；`npm run build` 通过（Build complete，仅 bundle 体积既有警告）
- [x] 左菜单/右列表布局不受影响；抽屉打开关闭数据正确刷新（订单/退款/重置申请列表刷新时按主键同步已开抽屉数据）

## 6. Change Log

| Date | Status | Change |
|---|---|---|
| 2026-09-06 | planned | 初版：统一管理端 CRUD 抽屉模式 |
| 2026-09-06 | implemented | React+Vue 双端 7 页抽屉化落地，双端 build 通过，移入 implemented |
