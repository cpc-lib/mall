---
name: "mall-dual-frontend-sync"
description: "Checklist for parallel changes across payment-demo-react and payment-demo-vue malls: file anchor map, dual build verification, spec ledger sync. Invoke for any mall frontend/UI change."
---

# 双商城前端对等改造清单（React 商城 ↔ Vue 商城）

本工作区有两套对等的**用户商城**前端，任何商城前端改动必须双端对等落地。
管理后台（payment-demo-react-admin :3002 / payment-demo-vue-admin :3003）不在本清单范围。

## 0. 前置：按 AGENTS.md 分类

- Design change（工作流/布局/主题/组件库换肤等）→ 先建 `spec/planned/<domain>/<NAME>_SPEC.md`（status:
  planned，含验收标准、实施锚点、兼容性/回滚说明），再改代码。
- Local bug（单一行为错误）→ 最小修复 + 构建验证，spec 补 Change Log 即可。
- 分支名：`feature/<topic>` / `bug-fix/<topic>` / `refactor/<topic>` / `update/<topic>`，不含 agent/厂商署名。

## 1. 双端文件锚点对照表

| 功能         | React（payment-demo-react/src）                                                 | Vue（payment-demo-vue/src）                                                   |
|------------|-------------------------------------------------------------------------------|-----------------------------------------------------------------------------|
| 技术栈        | React 18 + antd 5 + vite，HashRouter，dev :3000                                 | Vue 2 + Element UI 2.15 + vue-cli，dev :3000                                 |
| 主题 CSS     | `assets/css/mobile.css`（旧主题 `taobao.css` 保留不删，回滚切回 import）                    | `assets/css/mobile.css`（同左，另含 Element 覆盖段）                                  |
| 入口/换肤      | `main.jsx`（import css；ConfigProvider token: colorPrimary/borderRadius）        | `App.vue`（script 内 import css；`<router-view/>` 包 `<main class="app-main">`） |
| 顶部 App Bar | `components/AppHeader.jsx`                                                    | `components/AppHeader.vue`（**必须保留** `payment-auth-changed` 事件监听 + authTick） |
| 底部 Tab 栏   | `components/AppFooter.jsx`（NavLink，useLocation/end 判断 active）                 | `components/AppFooter.vue`（router-link，`$route.path` 判断）                    |
| 首页商品       | `pages/Home.jsx`（`.tb-grid/.tb-card`）                                         | `views/index.vue`（同名 class）                                                 |
| 购物车        | `pages/Cart.jsx`                                                              | `views/Cart.vue`                                                            |
| 订单/支付      | `pages/OrdersV2.jsx`（antd Table `scroll={{x}}`、Modal、`QRCodeSVG`）             | `views/Orders.vue`（el-table 自动横滚、el-dialog、`qriously`）                      |
| 退款申请       | `pages/RefundApplications.jsx`                                                | `views/RefundApplications.vue`                                              |
| 用户中心       | `pages/Account.jsx`                                                           | `views/Account.vue`（快捷入口 `.m-quick` + 演示声明 `.m-demo-note`）                  |
| 登录/成功页     | `pages/Login.jsx`、`pages/Success.jsx`                                         | `views/Login.vue`、`views/Success.vue`                                       |
| 商品图工具      | `assets/mallImgs.js`（`mallImg` / `onMallImgError` / `MALL_IMG_FALLBACK`）      | `assets/mallImgs.js`（同名导出，Vue 需在 methods 注册后模板可用）                           |
| 认证/API     | `utils/authStore.js`（localStorage key 前缀 **`mall_`**，含旧 key 角色化迁移）、`api/*.js` | `utils/authStore.js`（同左；管理后台两工程用 `admin_` 前缀，**禁止四端改回同名 key**，否则同源登录态互踩）    |

两端共用 class 命名约定：布局 `tb-*`（旧淘宝风遗留名，继续复用）、手机壳 `m-*`（
`.m-bar/.m-tabbar/.m-tab/.m-quick/.m-demo-note`）、二维码框 `.qr-frame`。

## 2. 改动规则（踩坑固化）

1. **对等原则**：React 改了哪个文件/样式/行为，Vue 必须有对应改动；class 名两端一致，CSS 规则逐条对齐。
2. **换肤/覆盖旧主题时**：旧主题文件里的**结构型属性**（`display`/`position`/`overflow`/`flex`/`aspect-ratio`/
   `object-fit`/`grid-template-columns`）必须核对带齐，新主题只替换**视觉型属性**（颜色/圆角/阴影/字号）。
    - 教训：`.tb-grid` 漏 `display:grid` → 商品卡退化为整行堆叠；`.tb-card-img` 漏方盒 → 远程竖图撑破整页。
3. **远程资源免疫**：图片/二维码等外部资源，容器必须对「任意比例图 / 加载中占位图 / 加载失败」免疫——固定比例盒（
   `aspect-ratio:1/1` 或 padding-top 法）+ `overflow:hidden` + img `position:absolute; inset:0; object-fit:cover` +
   `onError` 兜底到本地 data-URI 占位图。
4. **Vue SFC**：分段小 Edit，避免整段重写导致模板闭合错误；改完回读 `<template>` 关键片段确认。
5. **不删旧文件**：`taobao.css` 等停止 import 但保留；回滚 = 切回 import + git 还原。
6. 路由路径、API 请求/响应结构、组件库版本不动；纯视觉改造不引入新依赖。
7. **落盘回读（必做）**：本工作区出现过 Edit 成功后文件内容被回退（CSS 规则丢失、JS import 丢失）。**同一文件的多个 Edit
   严禁放在同一并行批次**——并行 Edit 同文件会因写竞争互相覆盖（后写者基于旧快照，先写者的改动丢失），必须一条消息只改同一文件一次、串行进行；不同文件可并行。每轮改完必须用
   Grep/Read 回读关键改动确认在磁盘上；尤其注意「JSX/模板引用了某标识符但 import 行被回退」的情况——Vite/vue-cli 构建*
   *不校验未定义标识符**，build 能过但运行时 `ReferenceError` 崩页，双 build 通过不代表无此问题。

## 3. 双 build 验证（串行，勿并行）

并行构建曾出现瞬时失败；一次只跑一个，失败先单独重跑确认：

1. `npm run build`（cwd: payment-demo-react）→ 成功标志：`✓ built in Ns`
2. `npm run build`（cwd: payment-demo-vue）→ 成功标志：`DONE  Build complete`

环境注意：

- Windows PowerShell 5 不支持 `&&`，多命令用 `;` 分隔。
- chunk/asset size 超限警告是既有现象，非失败。
- 前端改动不触及后端契约时无需跑 Java 测试；若改动涉及 API/DB/契约，另按 AGENTS.md 跑
  `mvn "-Dtest=PublicApiCharacterizationTest,InfrastructureBehaviorCharacterizationTest" test` 并更新对应 spec。

## 4. spec 状态同步（代码合入前必做）

1. 实现完成且双 build 通过后，编辑 spec：
    - `Status: planned` → `Status: implemented`；
    - 验收项 `[ ]` → `[x]` 并附构建证据（如「vite build ✓ / vue-cli build ✓ 日期」）；
    - Change Log 追加：`| 日期 | implemented | 改动摘要（文件锚点 + 验证结果） |`。
2. 移动归档：`Move-Item spec/planned/<domain>/X_SPEC.md spec/implemented/<domain>/X_SPEC.md`（PowerShell 用 `;` 不用
   `&&`）。
3. 部分交付：留在 `planned/`，已完成/未完成验收项分别标注；废弃决策入 `spec/archived/` 并注明原因日期。
4. 实现、测试、文档、spec 四者不一致 = 未完成。

## 5. 完成定义（DoD）

- [ ] 双端文件锚点对照表中每处改动都有对等落地
- [ ] 结构型 CSS 属性已与旧主题核对（无 display/方盒/flex 丢失）
- [ ] React build ✓、Vue build ✓（串行执行通过）
- [ ] spec status/验收项/Change Log 已更新并移入 `spec/implemented/`
