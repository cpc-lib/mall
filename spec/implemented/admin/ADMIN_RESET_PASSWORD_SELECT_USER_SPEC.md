# 管理员重置密码页「先选用户再填密码」交互改造 Spec

- Date: 2026-09-06
- Status: planned
- Domain: admin（frontend，payment-demo-react-admin / payment-demo-vue-admin）
- Class: Design change（独立「重置用户密码」工具页工作流变更；后端 API 契约不变）

## 1. 背景与问题

管理端独立页「重置用户密码」（AdminResetPassword）当前为一个裸表单：管理员必须手动输入数字 userId + 新密码才能提交。问题：

- userId 无引导、易输错，且无法确认目标用户身份（用户名/角色/状态不可见）
- 与系统已有的用户搜索接口（`GET /api/admin/users?page&size&keyword`）能力脱节

## 2. 目标模式（验收标准）

改造为「**搜索筛选用户 → 勾选唯一目标用户 → 填写新密码 → 提交重置**」：

1. 页面上部为用户列表：关键字搜索框（用户名关键字，回车/按钮触发）+ 查询/重置；表格列：选择、ID、用户名、角色、状态、注册时间；分页（每页 10，复用 adminUsers 接口）。
2. 选择方式为**单选**（语义上重置密码只针对一个用户）：Vue 用 el-radio 列，React 用 Table rowSelection radio。ROLE_ADMIN 行禁止勾选（后端 `adminResetPassword` 本就拒绝 ROLE_ADMIN，报「该接口仅允许重置普通用户密码」），禁选行同时给出可见原因。
3. 列表下方为操作区：显示「已选用户：用户名（ID）」，未选时提示先选择用户；新密码输入框（至少 8 位，与后端 `@Size(min=8,max=64)` 一致）；「重置密码」按钮在未选用户或密码不足 8 位时禁用。
4. 提交复用既有接口 `POST /api/auth/admin/reset-password {userId, newPassword}`；成功提示「密码已重置，目标用户旧 Token 已全局失效」，清空密码框。
5. 双框架（React antd / Vue Element）功能与交互对等；左菜单/右列表布局不受影响。

## 3. 兼容性

- 纯前端交互改造：路由不变、接口不变、请求/响应结构不变。
- 用户列表页（UserList）详情抽屉内的重置密码入口保持原样，不在本次范围。
- 回滚 = git 还原本次提交。

## 4. 实施锚点

- Vue：`payment-demo-vue-admin/src/views/AdminResetPassword.vue`（重写模板与脚本，复用 `authApi.adminUsers` / `authApi.resetPassword`）
- React：`payment-demo-react-admin/src/pages/AdminResetPassword.jsx`（重写，复用 `authApi.adminUsers` / `authApi.adminResetPassword`）
- 后端约束锚点：`AuthServiceImpl.adminResetPassword`（拒绝 ROLE_ADMIN、用户不存在报错）、`AdminResetPasswordRequest`（newPassword 8–64）

## 5. 验收

- [x] Vue：搜索/分页用户表，单选普通用户（admin 行禁选），选中后填密码可重置；`npm run build` 通过（Build complete）
- [x] React：对等实现；`npm run build` 通过（✓ built in 4.09s）
- [x] 未选用户/密码不足 8 位时按钮禁用；admin 行不可选且有提示

## 6. Change Log

| Date | Status | Change |
|---|---|---|
| 2026-09-06 | planned | 初版：重置密码页改为先勾选用户再填密码 |
| 2026-09-06 | implemented | React+Vue 双端落地，双端 build 通过，移入 implemented |
