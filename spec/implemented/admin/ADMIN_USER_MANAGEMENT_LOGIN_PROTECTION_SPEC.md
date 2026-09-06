# ADMIN_USER_MANAGEMENT_LOGIN_PROTECTION_SPEC — 用户管理拆分 / 登录防爆破 / 找回密码

| 项 | 值 |
|---|---|
| 状态 | planned |
| 日期 | 2026-09-05 |
| 问题分类 | Design change（API 契约变更 + DB schema 变更 + 认证行为变更） |
| 需求来源 | 用户需求：① 用户列表与在线状态拆分（列表仅看禁用状态，详情页看在线状态）；② 密码输错 3 次锁定 10 分钟；③ 忘记密码可提交申请由管理员重置 |
| 已确认决策 | 锁定策略＝用户名 + IP 双维度；重置密码＝系统自动生成随机密码（一次性展示给管理员） |

## 1. 目标

1. `GET /api/admin/users` 不再返回 `online` 字段（列表仅保留禁用/启用等基础状态）；新增 `GET /api/admin/users/{id}` 返回用户详情 + 在线状态；React/Vue 管理端用户列表去掉在线列、用户名可点击进入用户详情页。
2. 登录防爆破：同一用户名或同一来源 IP 密码错误累计 3 次 → 对应维度锁定 10 分钟；锁定期间即使密码正确也拒绝并提示剩余时间；登录成功清除失败计数；管理员重置密码解除该用户名锁定。
3. 找回密码：未登录用户可提交找回申请（校验用户存在且非管理员、同账号无待处理申请）；管理员在管理端查看申请列表，处理时系统自动生成 12 位随机密码（响应一次性返回，仅存哈希），拒绝则记录原因；处理后该用户名登录锁定解除。

## 2. 数据库变更

- 2.1 全量脚本 `env/sql/dm8/payment_demo.sql`：新增 `t_password_reset_request`（id/username/remark/status/admin_remark/handled_by/create_time/update_time，索引 `idx_password_reset_status_time(status, create_time)`，触发器 `trg_password_reset_uptime`），并在 DROP 守卫块中加入该表。
- 2.2 增量脚本 `env/sql/dm8/upgrade_admin_auth_v3.sql`：幂等（user_tables/user_indexes 计数守卫 + 触发器先删后建），供已存在 V2 库升级。
- 2.3 `env/scripts/dm8/init-dm8-sql.sh` `--if-missing` 分支矩阵：
  - V2 签名 `t_payment_order` 存在 + V3 签名 `t_password_reset_request` 存在 → 跳过全部；
  - V2 存在 + V3 缺失 → 仅执行 `upgrade_admin_auth_v3.sql`；
  - V1 旧库（LEGACY_CORE=6）→ `upgrade_trading_model_v2.sql` + `upgrade_admin_auth_v3.sql`；
  - 全新库 → 全部执行。
  - 执行后验证 V3 签名表存在。

## 3. API 契约与兼容性影响

| 契约 | 变更 | 兼容性 |
|---|---|---|
| `GET /api/admin/users` | 响应移除 `online` 字段，保留 id/username/role/userStatus/createTime/updateTime | **破坏性**；React/Vue 前端同变更内同步更新 |
| `GET /api/admin/users/{id}` | 新增，详情含 `online` | 新端点 |
| 登录锁定提示 | 锁定时返回「密码错误次数过多，账号已锁定，请约X分钟后再试」/「当前网络环境已被临时锁定，请约X分钟后再试」；锁定期间正确密码也拒绝 | 新增行为 |
| `POST /api/auth/login` | 内部接入 LoginGuardService（签名加 clientIp，对外请求/响应不变；禁用用户正确密码仍返回「用户名或密码错误」且不计数，现状保留） | 兼容 |
| `POST /api/auth/password-reset-request` | 新增公开端点（body: username, remark） | 新端点 |
| `GET /api/admin/password-reset-requests`、`POST /api/admin/password-reset-requests/{id}/handle`、`POST .../{id}/reject` | 新增管理端点（handle 响应 data.newPassword 一次性返回，库中仅存哈希） | 新端点 |
| `POST /api/auth/admin/reset-password` | 行为增强：重置后清除目标用户名登录失败计数与锁定 | 增量 |
| `t_password_reset_request` 表 | 新增，纯增量 | 回滚：DROP 表即可；Redis 锁定键 TTL ≤ 10 分钟自愈 |

## 4. 后端模块设计

### 4.1 LoginGuardService（cc.ivera.security，@Service）

Redis 键语义：

| 键 | 含义 | TTL |
|---|---|---|
| `auth:login_fail:{username}` | 用户名失败计数（滑动窗口） | 600s（每次失败刷新） |
| `auth:login_lock:{username}` | 用户名锁定 | 600s |
| `auth:login_fail_ip:{ip}` | IP 失败计数（滑动窗口） | 600s（每次失败刷新） |
| `auth:login_lock_ip:{ip}` | IP 锁定 | 600s |

- `checkLocked(username, ip)`：任一锁定键存在即抛 BizException（含剩余分钟）；先于查库执行；Redis 异常 fail-open。
- `recordFailure(username, ip)`：双维度 increment + expire(600s)；计数 ≥ 3 → set 锁键(600s) 并删计数键；Redis 异常 fail-open。
- `clear/clearUsername/clearIp`：删对应 fail/lock 键。
- IP 来源：`X-Forwarded-For` 首值（64 字符截断），否则 `request.getRemoteAddr()`。

已知限制（接受并记录）：XFF 可伪造；NAT 共享 IP 可能误伤（10 分钟自愈）；恶意可锁他人用户名（管理员重置可解锁）；increment+expire 非原子，崩溃可能残留无 TTL 计数键（后续可用 Lua 加固，先例 AuthServiceImpl.refresh）。

### 4.2 登录流程（AuthServiceImpl.login 重写）

锁定检查 → 查用户 → 用户不存在或密码哈希不匹配（recordFailure + 统一「用户名或密码错误」）→ 禁用（现状统一报错，不计数）→ 成功（clear 双维度计数 + 发 token pair）。

### 4.3 找回密码域

- 实体 `PasswordResetRequest`（t_password_reset_request）+ Mapper；状态机 `PENDING → HANDLED | REJECTED`（仅 PENDING 可流转，重复流转报「该申请已被处理，请刷新列表」）。
- `PasswordResetRequestService`：submit（用户存在且非 ROLE_ADMIN、同用户名无 PENDING）；list（PENDING 优先，其余 createTime 降序）；handle（生成 12 位 `[A-Za-z0-9]` 随机密码 → 委托 `adminResetPassword`（bump token version + 清用户名锁定）→ 标记 HANDLED + handled_by = AuthContext.userId → 返回 newPassword）；reject（标记 REJECTED + handled_by）。
- 控制器拆分：公开提交在 `AuthController`；管理端在新 `AdminPasswordResetRequestController`（`/api/admin/password-reset-requests`，AdminUserController 已绑定 /users 无法承载该路径）。
- `AuthInterceptor.isPublic` 显式放行 `/api/auth/password-reset-request`；`/api/admin/` 路径由现有 requiresAdmin 规则覆盖。

## 5. 实现锚点

- [x] `payment-demo/src/main/java/cc/ivera/security/LoginGuardService.java`
- [x] `payment-demo/src/main/java/cc/ivera/service/impl/AuthServiceImpl.java`（login/adminResetPassword）
- [x] `payment-demo/src/main/java/cc/ivera/controller/AuthController.java`（login clientIp / password-reset-request）
- [x] `payment-demo/src/main/java/cc/ivera/security/AuthInterceptor.java`（isPublic）
- [x] `payment-demo/src/main/java/cc/ivera/controller/AdminUserController.java`（list 去除 online / 新增 detail）
- [x] `payment-demo/src/main/java/cc/ivera/controller/AdminPasswordResetRequestController.java`
- [x] `payment-demo/src/main/java/cc/ivera/service/impl/PasswordResetRequestServiceImpl.java`
- [x] `payment-demo/env/sql/dm8/payment_demo.sql`、`env/sql/dm8/upgrade_admin_auth_v3.sql`、`env/scripts/dm8/init-dm8-sql.sh`
- [x] React：`payment-demo-react/src/pages/{AdminConsole.jsx,UserDetail.jsx,Login.jsx,App.jsx}`、`src/api/auth.js`
- [x] Vue：`payment-demo-vue/src/views/{AdminConsole.vue,UserDetail.vue,Login.vue}`、`src/router/index.js`、`src/api/auth.js`

## 6. 测试锚点

- [x] `payment-demo/src/test/java/cc/ivera/security/LoginGuardServiceTest.java`
- [x] `payment-demo/src/test/java/cc/ivera/service/AuthServiceLoginLockoutTest.java`
- [x] `payment-demo/src/test/java/cc/ivera/controller/AdminUserManagementApiTest.java`
- [x] `payment-demo/src/test/java/cc/ivera/service/impl/PasswordResetRequestServiceTest.java`
- [x] `payment-demo/src/test/java/cc/ivera/database/Dm8MigrationContractTest.java`（扩展 V3 契约）
- [x] `payment-demo/src/test/java/cc/ivera/security/AuthInterceptorRoleRuleTest.java`（扩展放行用例）
- [x] 特征化回归：全量 `mvn test` 210/210 通过（含 PublicApiCharacterizationTest、InfrastructureBehaviorCharacterizationTest）

## 7. 验收标准

- [x] 新增单元测试全部通过；全量 `mvn test` 通过（210/210）；React/Vue `npm run build` 通过。
- [ ] 现有 V2 库执行 `init-dm8-sql.sh --if-missing` 仅运行 V3 升级脚本且幂等可重复执行（待重启 DM8 环境时验证）。
- [ ] 冒烟（需重启 Java 后端生效）：3 次错密码 → 第 4 次（含正确密码）被拒含锁定提示；管理员处理申请后返回一次性密码且该用户解锁；禁用用户行为不变；用户列表无 online、详情页有 online。

## 8. 变更记录

| 日期 | 状态 | 说明 | 来源 |
|---|---|---|---|
| 2026-09-05 | planned | 用户管理拆分 / 登录防爆破（用户名+IP）/ 找回密码（自动生成随机密码）设计完成 | 用户需求 + 决策确认 |
| 2026-09-05 | implemented | 后端/前端/DB 脚本/测试全部落地：全量 210 测试通过，React/Vue 构建通过；剩余 V3 升级脚本实库执行与重启后冒烟验证 | 本 spec |
