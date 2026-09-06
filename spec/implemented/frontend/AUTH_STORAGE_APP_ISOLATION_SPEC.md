# 管理后台与用户商城登录态存储隔离 Spec（四端 authStore）

- Date: 2026-09-06
- Status: implemented
- Domain: frontend（payment-demo-react / payment-demo-vue / payment-demo-react-admin / payment-demo-vue-admin）
- Class: Design change（前端本地会话存储 key 契约变更；后端 API、登录接口、token 结构不变）

## 1. 背景与问题

四个前端工程的 `src/utils/authStore.js` 最初使用**完全相同**的 localStorage key：
`payment_demo_access_token` / `payment_demo_refresh_token` / `payment_demo_user`。

同源访问时（生产同源部署；或两个商城端交替占用 :3000 端口），任一端登录会覆盖另一端的 token/user，退出登录会连另一端的 key 一起清掉，导致「A 端登录后 B 端登录用户丢失/无法再次访问」。管理后台（ROLE_ADMIN）与商城普通用户的会话相互踩踏。

## 2. 目标模式（验收标准）

### 2.1 key 按端命名空间隔离（当前契约，以 `.trae/skills/mall-dual-frontend-sync/SKILL.md` 锚点表为准）
- 两个**商城端**（payment-demo-react、payment-demo-vue）：
  - `mall_access_token` / `mall_refresh_token` / `mall_user`
- 两个**管理端**（payment-demo-react-admin、payment-demo-vue-admin）：
  - `admin_access_token` / `admin_refresh_token` / `admin_user`
- 同端双实现（React/Vue）共用前缀：二者是同一应用的两套实现，互通符合预期；管理端同理。

### 2.2 旧 key 一次性迁移（不强制重新登录，承接两代历史前缀）
模块加载时执行一次性迁移：仅当本端新 key（`mall_*` / `admin_*`）不存在时，按以下顺序承接：
1. **上一版隔离前缀**（角色已归属正确，直接搬运，不再判角色）：
   - 商城端读 `payment_demo_mall_access_token` / `_refresh_token` / `_user`；
   - 管理端读 `payment_demo_admin_access_token` / `_refresh_token` / `_user`。
2. **最初版共享前缀** `payment_demo_*`（需按角色判断归属）：
   - 商城端：旧 `user.role !== 'ROLE_ADMIN'` 才迁移（管理员登录态不进商城）；
   - 管理端：旧 `user.role === 'ROLE_ADMIN'` 才迁移。
- 不删除任何旧 key（避免另一端尚未打开时误删其迁移源）；旧 key 此后变为惰性数据，不再被读写。
- 迁移只搬运本地既有数据，不发起任何请求。

### 2.3 行为不变
- `getAccessToken / getRefreshToken / getUser / saveAuth / clearAuth` 函数签名与调用方完全不变；Vue 端保留 `payment-auth-changed` 事件派发。
- 后端登录/刷新/登出接口、token 载荷、路由守卫规则（管理端 RequireAdmin 拦非管理员）均不变。
- 隔离后：管理端登录/退出不影响商城端登录态，反之亦然；同浏览器可同时保持两类会话。

## 3. 兼容性

- 前端本地存储 key 变更：两代旧登录态均按 2.2 自动迁移；无法迁移的场景（旧 key 已被他端覆盖）表现为未登录，重新登录一次即可。
- 不涉及后端、DB、API 契约变更；不需要后端发版。
- 回滚 = git 还原本次提交（旧 key 数据仍在，可继续用）。

## 4. 实施锚点

- `payment-demo-react/src/utils/authStore.js`（`mall_` 前缀 + 两代迁移）
- `payment-demo-vue/src/utils/authStore.js`（`mall_` 前缀 + 两代迁移，保留 auth-changed 事件）
- `payment-demo-react-admin/src/utils/authStore.js`（`admin_` 前缀 + 两代迁移）
- `payment-demo-vue-admin/src/utils/authStore.js`（`admin_` 前缀 + 两代迁移，保留 auth-changed 事件）
- 契约锚点：`.trae/skills/mall-dual-frontend-sync/SKILL.md` 锚点表（mall_ / admin_）
- 四端 `npm run build` 串行验证。

## 5. 验收

- [x] 四端 authStore 使用各自前缀 key（mall ×2 / admin ×2），函数签名不变
- [x] 两代旧 key 一次性迁移逻辑就位：上一版隔离前缀直接搬运；最初版共享 key 按角色迁移（mall 拒管理员 / admin 仅管理员）；不删旧 key
- [x] 商城端登录、管理端登录互不覆盖；clearAuth 只清本端前缀 key
- [x] 四端 `npm run build` 全部通过

## 6. Change Log

| Date | Status | Change |
|---|---|---|
| 2026-09-06 | planned | 初版：管理后台/商城登录态 localStorage key 按端隔离 + 旧 key 角色化迁移 |
| 2026-09-06 | implemented | 四端 authStore 落地前缀隔离（mall: `payment_demo_mall_*`，admin: `payment_demo_admin_*`），模块加载时按角色一次性迁移旧共享 key（不删旧 key）；Vue 两端保留 payment-auth-changed 事件；四端 build 通过 |
| 2026-09-06 | implemented | key 前缀按 SKILL.md 锚点表简化：商城端 `mall_*`、管理端 `admin_*`；迁移逻辑扩展为承接两代历史前缀（优先 `payment_demo_mall_*`/`payment_demo_admin_*` 直接搬运，再按角色承接 `payment_demo_*`），已登录用户不掉线；四端 build 通过 |
