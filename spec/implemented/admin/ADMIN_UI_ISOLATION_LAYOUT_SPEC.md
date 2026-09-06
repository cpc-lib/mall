# 管理端/用户端 UI 隔离与左侧导航布局（Admin UI Isolation & Layout）Spec

- Date: 2026-09-05
- Status: planned
- Domain: admin
- Class: Design change（双端页面结构、路由、导航重构；纯 UI 层，后端 API 不变）

## 1. Background

- 现状：管理端功能混杂在前台（商城头导航内含管理员入口），AdminConsole 以页签承载全部管理功能，详情（用户/商品）为独立整页跳转。
- 用户要求：管理员端与普通用户端在 UI 层面隔离；管理端（后台）UI 采用「左侧功能导航 + 右侧列表 + 详情抽屉」布局。

## 2. Contract

### 2.1 隔离原则（双端一致，仅 UI 层）

- 管理端：所有管理页面统一收敛到 `/#/admin/*`，使用独立 `AdminLayout`（自带顶栏，不渲染商城公共头/底/页脚）。
- 用户端：保持现有商城风格（公共头 + 内容 + 公共底），普通用户看不到任何管理入口。
- 前台头部（AppHeader）：管理员仅显示 `商品 / 用户中心 / 管理后台 / 退出登录`（隐藏购物车/我的订单/我的退款）；普通用户不变。
- 登录后跳转：管理员 → `/admin/orders`；普通用户 → 原 redirect 或 `/cart`。

### 2.2 AdminLayout（React antd Layout / Vue el-container）

- 顶栏：`支付业务演示 · 管理后台` + 右侧 `前台首页`、当前用户名、`退出登录`。
- 左侧菜单（12 项，双端一致）：
  1. 订单管理 `/admin/orders`（原 AdminConsole「订单管理」页签 + 差价退款入口）
  2. 订单发货 `/admin/shipping`
  3. 商品库存 `/admin/products`（含新增商品、行内调库存/上下架；商品名点击打开详情抽屉）
  4. 退款受理 `/admin/refunds`
  5. MQ/库存异常 `/admin/mq-logs`
  6. 用户列表 `/admin/users`（用户名点击打开用户详情抽屉，含在线状态与重置密码）
  7. 密码重置申请 `/admin/reset-requests`
  8. 重置用户密码 `/admin/reset-password`（手动 userId 重置）
  9. 批量库存维护 `/admin/stock-maintenance`
  10. 下载账单 `/admin/download`
  11. 支付配置 `/admin/payment-config`
  12. 对账管理 `/admin/reconciliation`
- 右侧内容区：渲染当前菜单对应页面（列表为主体）。
- `/admin` 首页重定向到 `/admin/orders`。

### 2.3 详情抽屉（Drawer）

- 用户详情：UserList 内点击用户名 → 抽屉展示 `GET /api/admin/users/{id}`（含 online），抽屉内提供 `重置密码`（复用现有 ≥8 位弹窗）；原 `/admin/user/:id` 整页删除。
- 商品详情：AdminProducts 内点击商品名 → 抽屉展示商品信息 + 库存调整 + 上下架 + 最近 20 条库存日志（复用 `GET /api/admin/products/{id}`）；原 `/admin/product/:id` 整页删除。

### 2.4 路由与兼容

- 新结构：`/admin/*` 挂 AdminLayout（React Outlet 嵌套 / Vue 子路由），守卫不变（RequireAdmin / requiresAdmin）。
- `/admin/stock-edit/:id`（全屏 Excel 编辑器新页签）保持在布局外、全屏渲染，行为不变。
- 兼容重定向（旧书签）：`/admin-console`→`/admin/orders`；`/admin/user/:id`→`/admin/users`；`/admin/product/:id`→`/admin/products`；`/download`、`/payment-config`、`/reconciliation`→对应 `/admin/*` 新路径。
- 删除文件：React/Vue 的 `AdminConsole`、`UserDetail`、`ProductDetail` 页面。

## 3. Acceptance Criteria

- [x] 管理员登录后直达 `/admin/orders`，后台为「左导航 + 右内容」，不含商城头/底；前台头部管理员仅见「管理后台」入口。
- [x] 12 个菜单项均可到达且功能与拆分前等价（订单管理/发货/商品库存/退款受理/MQ异常/用户列表/密码重置申请/重置密码/批量库存维护/下载账单/支付配置/对账管理）。
- [x] 用户详情、商品详情以抽屉打开，原详情整页路由重定向不 404。
- [x] 普通用户访问 `/admin/*` 仍被守卫拦截；`/admin/stock-edit/:id` 新页签编辑器不受影响。
- [x] React / Vue 生产构建通过。

## 4. Compatibility / Rollback

- 纯前端 UI 变更，后端 API、请求/响应契约零变化。
- 旧路由全部 301 式重定向到新路径，无 404；如需回滚恢复旧文件与路由即可，无数据迁移。

## 5. Implementation Anchors

- React: `payment-demo-react/src/components/AdminLayout.jsx`、`src/pages/Admin{Orders,Shipping,Products,Refunds,MqLogs,ResetRequests,ResetPassword}.jsx`、`src/pages/UserList.jsx`（抽屉）、`src/App.jsx`、`src/components/AppHeader.jsx`、`src/pages/Login.jsx`、`src/pages/StockMaintenance.jsx`
- Vue: `payment-demo-vue/src/components/AdminLayout.vue`、`src/views/Admin*.vue`、`src/views/UserList.vue`（抽屉）、`src/router/index.js`、`src/components/AppHeader.vue`、`src/views/Login.vue`、`src/views/StockMaintenance.vue`、`src/App.vue`
- Tests: 无后端变更；前端验证 = 双端生产构建 + 人工核对路由/守卫。

## 6. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-09-05 | planned | 初版：双端 UI 隔离 + 管理后台左导航布局 + 详情抽屉化。 | User request |
| 2026-09-05 | implemented | 双端落地：AdminLayout（顶栏+左菜单12项+右内容）；AdminConsole 拆分为 7 个独立页面；UserList/AdminProducts 内嵌详情抽屉；管理员登录直达 /admin/orders；AppHeader 管理员仅留「管理后台」；旧路由全部重定向；双端生产构建通过；AdminConsole/UserDetail/ProductDetail 已删除。 | 同上 |
