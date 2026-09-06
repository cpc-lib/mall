# 用户端淘宝风格重设计与管理端独立工程拆分（Mall Redesign & Admin Split）Spec

- Date: 2026-09-05
- Status: planned
- Domain: frontend
- Class: Design change（工程结构拆分 + 用户端 UI 全面重设计；纯前端，后端 API 不变）

## 1. Background

- 上一轮已完成双端管理后台「左导航 + 右内容 + 详情抽屉」布局，但管理端与用户端仍共存于同一工程。
- 用户要求：① 普通用户 UI 重设计为淘宝风格商城；② 管理端 UI 拆分为独立的 React 管理端工程与 Vue 管理端工程。

## 2. Contract

### 2.1 工程结构（一分为四）

| 工程 | 用途 | 端口(dev) | 说明 |
|---|---|---|---|
| `payment-demo-react` | 用户商城（React + antd5） | 3000 | 淘宝风格，无任何管理功能 |
| `payment-demo-vue` | 用户商城（Vue2 + ElementUI） | 3000 | 淘宝风格，无任何管理功能 |
| `payment-demo-react-admin` | 管理后台（React + antd5） | 3002 | 独立工程，自带登录页 |
| `payment-demo-vue-admin` | 管理后台（Vue2 + ElementUI） | 3003 | 独立工程，自带登录页 |

- 管理端工程内容 = 现有管理页面平移（AdminLayout 左导航布局、12 菜单项、用户/商品详情抽屉、批量库存维护、全屏 Excel 编辑器、下载账单、支付配置、对账管理）+ 管理端专用登录页。
- 管理端登录页：登录成功且角色为 `ROLE_ADMIN` → `/admin/orders`；非管理员 → 提示「仅管理员可登录本系统」并清除本地凭证。
- 用户端删除全部管理页面/路由/api（adminStock、bill、reconciliation 等管理专用 api 一并移除）；`paymentConfig.listEnabledApps` 为购物车结算所需，保留在用户端。
- 后端已 `@CrossOrigin` 全开，多端口前端直连 8080 不受影响。

### 2.2 用户端淘宝风格设计（React / Vue 同构）

- 主题色：淘宝橙 `#FF5000`（hover `#FF6A00`），辅助浅灰 `#F5F5F5`、深灰文字 `#3C3C3C`。
- 头部（AppHeader）三层结构：
  1. 顶部通栏（浅灰底、小字号）：未登录「亲，请登录 / 免费注册」；已登录「hi，{用户名} / 退出登录」；右侧「我的订单 / 我的退款 / 购物车 / 用户中心」。
  2. 主头部（白底）：橙红品牌 logo「淘支付」+ 大搜索框（灰底圆角 + 橙色搜索按钮）。
  3. 橙色主导航条：首页 / 全部商品 / 我的订单 / 我的退款 / 用户中心。
- 首页（Home / index）：瀑布式商品卡片网格（auto-fill 220px）：商品图（text_to_image 生成的 6 张分类商品图按 id 轮换）、标题两行截断、橙色价格 ¥xx.00、库存与状态标签、hover 上浮阴影、「加入购物车」橙色按钮；管理员账号只读提示。
- 购物车（Cart）：淘宝式结算页 —— 白卡容器、商品图 + 标题、勾选、单价（橙）、数量 stepper、小计、删除；底部吸底结算条（已选 N 件 · 合计 ¥xx.xx · 橙色大按钮「结 算」）；收货人信息与支付应用选择在结算条上方表单区。
- 登录（Login）：居中白卡 + 顶部橙色标题条、登录/注册 tab、忘记密码入口；管理员登录后跳首页（前台无管理入口）。
- 其余页面（我的订单 / 我的退款 / 用户中心 / 支付成功）：保持现有功能与表格结构，通过全局主题色（React ConfigProvider token / Vue CSS 覆盖 Element 主色）与白卡容器统一观感。
- React 端 `main.jsx` 增加 antd ConfigProvider `theme.token.colorPrimary=#FF5000`；Vue 端新增 `taobao.css` 覆盖 Element 主按钮/链接等主色。
- 商品图片：使用 `text_to_image` API（square）生成 6 张电商产品图（耳机/卫衣/马克杯/手表/运动鞋/背包），前端常量数组按 `id % 6` 轮换。

### 2.3 路由变化

- 用户端（双端）：`/admin/*` 全部路由与页面移除；管理员在用户端登录后跳 `/`（首页）。
- 管理端（双端）：`/login`（管理登录）+ `/admin/*`（既有 12 项布局路由）+ `/admin/stock-edit/:id` 全屏编辑器 + `/` → `/admin/orders` 重定向；非管理员访问被守卫拦截回登录页。

## 3. Acceptance Criteria

- [ ] 4 个工程 `npm run build` 全部通过。
- [ ] 管理端两工程：登录 → 左导航后台，12 菜单 + 详情抽屉 + Excel 编辑器可用；非管理员无法进入。
- [ ] 用户端两工程：无任何管理页面/路由/api 残留；淘宝风头部/首页/购物车/登录；主色 #FF5000 贯穿。
- [ ] 管理端功能与拆分前等价（页面平移，无行为变更）。

## 4. Compatibility / Rollback

- 纯前端拆分：后端零改动；新旧工程共享同一后端。
- 旧书签 `/#/admin/*` 在用户端工程失效（跳转首页），请改用管理端工程地址；管理端路由结构不变。
- 回滚 = 恢复 git 上一版本（工程目录级回退）。

## 5. Implementation Anchors

- 新工程：`payment-demo-react-admin/`、`payment-demo-vue-admin/`（package.json / vite.config / vue.config / index.html / App / router / Login）
- 用户端：`payment-demo-react/src/{App.jsx,main.jsx,components/AppHeader.jsx,pages/Home.jsx,pages/Cart.jsx,pages/Login.jsx,assets/css/taobao.css}`、`payment-demo-vue/src/{App.vue,router/index.js,components/AppHeader.vue,views/index.vue,views/Cart.vue,views/Login.vue,assets/css/taobao.css}`
- 商品图常量：两端 `src/assets/mallImgs.js`

## 6. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-09-05 | planned | 初版：四工程拆分 + 用户端淘宝风重设计。 | User request |
| 2026-09-05 | implemented | 落地：新建 payment-demo-react-admin(3002)/payment-demo-vue-admin(3003)（管理端专用登录页 + 12 菜单布局平移 + Luckysheet 全屏编辑器）；用户端两工程删除全部管理页面/api/luckysheet，淘宝橙 #FF5000 主题（antd ConfigProvider / Element CSS 覆盖），淘宝风头部（通栏+搜索+橙导航）、商品卡片网格（text_to_image 商品图）、购物车结算条、登录卡；移除 xlsx 依赖；四工程构建通过。 | 同上 |
