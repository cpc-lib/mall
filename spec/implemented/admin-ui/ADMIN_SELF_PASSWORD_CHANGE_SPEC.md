# 管理后台自助修改密码 SPEC

- Status: implemented
- 日期: 2026-09-07
- 范围: admin-ui（后端契约零变更，复用既有接口）
- 分类: Design change（管理后台新增自助工作流；无 API/DB/响应结构变更）

## 背景

管理后台顶栏仅有「前台首页 / 用户名 / 退出登录」，管理员修改自己密码只能走前台商城 Account 页或找超管重置，闭环缺失。后端已有自助改密接口且 admin-ui 的 API 封装已就绪，仅缺 UI 入口。

## 复用的既有契约（无后端改动）

- 接口: `POST /api/auth/password`（[AuthController.java:50](../../../backend/src/main/java/cc/ivera/controller/AuthController.java)）
- 请求: `{ oldPassword, newPassword }`，newPassword 8-64 位（`PasswordChangeRequest` 校验）
- 会话行为: 改密成功后 `bumpVersion` 使该用户全部 Token 全局失效 → 前端必须清凭据并回登录页
- 错误: 原密码错误等由 axios 拦截器统一弹出提示

## 实施锚点（计划）

1. `admin-ui/src/components/AdminLayout.jsx`
   - 顶栏「退出登录」左侧新增 `修改密码` 文本按钮
   - 点击弹出 Modal（对齐用户偏好：弹窗式而非页面直显）
   - 表单: 原密码（必填）/ 新密码（必填，8-64 位）/ 确认新密码（与新密码一致校验）
   - 提交 → `authApi.changePassword` → 关弹窗 → `clearAuth()` → `message.success('密码修改成功，请重新登录')` → `nav('/login')`（与 user-ui Account.jsx 行为一致）

## 不改动

- 后端任何代码、路由、请求/响应结构
- 其余页面与既有重置密码功能（AdminResetPassword/UserList 面向普通用户，语义不同）

## 验收标准

- [x] 管理员登录后顶栏可见「修改密码」入口，弹窗可打开（AdminLayout.jsx:50 入口 / :64 Modal）
- [x] 原密码错误时由拦截器提示，弹窗不关闭（changePassword 无 catch，拦截器统一弹出）
- [x] 修改成功后清凭据跳转登录页，旧 Token 失效（clearAuth + nav('/login')，后端 bumpVersion 行为）
- [x] `npm run build`（admin-ui）通过（vite build ✓ 3.88s，2026-09-07）
- [x] `npm run test:logic`（admin-ui）通过（1 pass / 0 fail，2026-09-07）

## 回滚

git 还原 `admin-ui/src/components/AdminLayout.jsx` 与本 spec；无数据/契约迁移。

## Change Log

| 日期 | 状态 | 摘要 |
|---|---|---|
| 2026-09-07 | implemented | AdminLayout 顶栏新增「修改密码」入口 + Modal 表单（原密码/新密码 8-64 位/确认校验），复用 POST /api/auth/password，成功后清凭据回登录页。vite build ✓ 3.88s / test:logic 1 pass 0 fail |
