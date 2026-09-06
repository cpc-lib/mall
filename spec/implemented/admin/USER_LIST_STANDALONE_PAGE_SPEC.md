# 用户列表独立页面（User List Standalone Page）Spec

- Date: 2026-09-05
- Status: implemented
- Domain: admin
- Class: Design change（新增管理端点、列表契约由全量数组升级为分页结构、双端页面结构变化）

## 1. Background

- 管理台双端已有「用户管理」页签（React/Vue AdminConsole 内嵌，全量数组列表），以及用户详情页 `/admin/user/:id`（含在线状态，由 Redis refresh token 判定）。
- 用户要求将用户列表升级为独立页面（管理台头部入口），并补齐搜索、分页、禁用/启用等管理能力，双端同步对齐。

## 2. Contract

### 2.1 Backend（`/api/admin/users`，admin required）

- `GET /api/admin/users?page=1&size=10&keyword=`：分页 + 用户名模糊搜索。
  - `page` 默认 1（≥1）；`size` 默认 10（钳制 1-100）；`keyword` 非空时按 `username LIKE '%keyword%'` 过滤。
  - 响应 `R<Map>`：`{ total, page, size, records }`；`records` 行字段 `id/username/role/userStatus/createTime/updateTime`。
  - **列表不返回 online 字段**（沿用硬性约束：列表仅展示禁用状态，在线状态仅通过详情接口获取）。排序 `id` 升序。
- `GET /api/admin/users/{id}`：不变（含 `online`）。
- `PUT /api/admin/users/{id}/status`：body `{ "userStatus": "ENABLED" | "DISABLED" }`。
  - 非法值抛 `用户状态仅支持 ENABLED / DISABLED`；用户不存在抛 `用户不存在`；目标为 `ROLE_ADMIN` 抛 `仅允许操作普通用户账号`。
  - 置为 `DISABLED` 时同步 `auth:token_version:{id}` 自增（等价 bumpVersion）：该用户 Access Token 立即全局失效，Refresh Token 在刷新时因版本与状态双重校验失效（登录/刷新本就拒绝禁用用户，见 AuthServiceImpl）。
  - 重复设置同状态幂等成功。响应 `R<Map>`（id/username/role/userStatus）。

### 2.2 Frontend（React / Vue 同契约）

- 新增独立页面 `/#/admin/users`（React `src/pages/UserList.jsx`、Vue `src/views/UserList.vue`，RequireAdmin 路由守卫）：
  - 搜索栏：用户名关键字输入 + 查询 / 重置。
  - 表格列：ID / 用户名（点击进入 `/admin/user/:id` 详情）/ 角色 / 状态（ENABLED 绿、DISABLED 红）/ 注册时间 / 操作。
  - 操作列（仅 `ROLE_USER` 行）：`重置密码`（弹窗输入 ≥8 位新密码，成功提示旧 Token 失效）+ `禁用`/`启用`（禁用需确认：强制下线且无法登录）。
  - 分页：每页 10 条，后端分页。
- AdminConsole 双端：头部新增 `用户列表` 入口按钮；移除原「用户管理」内嵌页签（避免双入口）。
- 用户详情页返回按钮改为回 `/admin/users`。

## 3. Acceptance Criteria

- [ ] 列表接口分页/keyword 生效，行不含 online；状态切换接口校验合法值/目标角色并踢下线。Locked by `AdminUserManagementApiTest`（更新 + 新增用例）。
- [ ] 双端 `/admin/users` 页面：搜索、分页、重置密码、禁用/启用、点击用户名进详情；行为一致。
- [ ] 管理台头部入口 + 移除内嵌页签；详情页返回指向列表。
- [ ] React / Vue 生产构建通过。

## 4. Compatibility / Rollback

- 列表响应结构由数组升级为分页 Map：唯一消费方为原管理台页签（本次同步移除并迁移到新页面），无遗留调用方；旧结构不保留。
- 新增端点为纯增量；如需回滚，前端退回页签 + 后端还原 `list()` 即可，无数据迁移。

## 5. Implementation Anchors

- Backend: `AdminUserController.list()/detail()/setStatus()`（`payment-demo/src/main/java/cc/ivera/controller/AdminUserController.java`）
- Backend DTO: `cc.ivera.dto.admin.UserStatusRequest`
- Frontend React: `payment-demo-react/src/pages/UserList.jsx`、路由 `src/App.jsx`（`/admin/users`）、`src/api/auth.js`（adminUsers(params)/setUserStatus）、`src/pages/AdminConsole.jsx`（头部入口 + 移除页签）、`src/pages/UserDetail.jsx`（返回列表）
- Frontend Vue: `payment-demo-vue/src/views/UserList.vue`、路由 `src/router/index.js`（`/admin/users`）、`src/api/auth.js`、`src/views/AdminConsole.vue`、`src/views/UserDetail.vue`
- Tests: `payment-demo/src/test/java/cc/ivera/controller/AdminUserManagementApiTest.java`

## 6. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-09-05 | planned | 初版：独立用户列表页 + 分页/搜索/禁用启用契约。 | User request: 用户列表功能补齐并对齐双端设计 |
| 2026-09-05 | implemented | 双端落地：后端分页/keyword/状态切换 + 测试通过；React/Vue UserList 页 + 路由 + 管理台头部入口 + 移除内嵌页签；详情页返回列表；双端构建通过。修复 Vue 路由遗留问题：补齐 `/admin/stock-edit/:id` 路由（StockExcelEditor 已存在但未挂载）。 | 同上 |
