# MALL_REFINED_UI_PAY_CHANNEL_SELECT_SPEC — 商城精致现代风 UI 重构 + 支付渠道列表选择

- 状态：`implemented`
- 日期：2026-09-06
- 问题分类：Design change（前端主题/视觉体系 + 结算支付方式选择交互）
- 需求来源：用户反馈 React 商城页面 UI 风格丑、要求优化设计；支付渠道应以列表展示并由用户选择后支付。
- 设计方向：用户确认采用「精致现代风」——去卡通硬阴影/满屏渐变，白卡片+柔和阴影+统一克制的电商橙红主色+规范间距字号。

## 1. 问题与目标

### 1.1 现状问题

| 问题 | 表现 |
|---|---|
| 卡通硬阴影 | `4px 4px 0`/`7px 7px 0` 等硬偏移阴影（动漫风遗留），观感廉价 |
| 色彩混乱 | 粉色系 token + 内联硬编码 `#FF5000`/`#6C6C6C`/`#9C9C9C` 混杂，双色系打架 |
| 层次不足 | 1.5px/2px/3px 重边框 + 粉色线，标题字重 800，缺细分隔 |
| 支付方式选择弱 | 购物车仅「支付应用」Select 下拉（后端概念），无渠道视觉列表 |

### 1.2 目标（双端对等，React :3000 / Vue :3000）

1. **设计 token 收敛**：主色 `--m-primary:#fa5416`（电商橙红）+ 中性灰阶（ink/ink-2/muted/line）+ 语义色（微信绿 `#07c160`、支付宝蓝 `#1677ff`、成功绿）。
2. **视觉替换、结构保留**：硬阴影 → 柔和阴影（`0 2px 8px rgba(31,35,41,.06)` 级）；粉色线/重边框 → 1px 中性 hairline；渐变按钮 → 实色主色；圆角三档统一（卡片 14px / 控件 10px / 药丸 999px 仅搜索·Tab·主 CTA）。
3. **结构型属性零丢失**：`#app` flex 壳、`.m-bar` sticky、`.m-tabbar` fixed flex、`.tb-grid` grid 2 列、`.tb-card` flex 列 + `.tb-card-img` 方形图盒（aspect-ratio/object-fit/overflow）+ `.tb-cart-row` order 排布 + `.qr-frame` 结构全部保留（换肤只动视觉属性）。
4. **支付渠道卡片列表**（购物车结算区）：`支付方式` 标题 + 渠道卡片单选列表（品牌色块 logo「微/支」+ 名称 + 描述 + 右侧选中 radio 勾），选中橙色高亮；结算按所选渠道映射启用支付应用（`channelCode` → 该渠道第一个启用应用），下单契约不变（仍提交 `paymentType` + `paymentAppId`）。
5. **内联硬编码色清理**：Cart 内联 `#FF5000/#6C6C6C/#9C9C9C` 等改为 token 引用（CSS class 或 `var(--m-*)`）。

## 2. 文件锚点（双端）

| 改动 | React（payment-demo-react/src） | Vue（payment-demo-vue/src） |
|---|---|---|
| 主题 CSS 重写 | `assets/css/mobile.css`（antd 换肤段） | `assets/css/mobile.css`（Element 换肤段同步重写） |
| 支付渠道卡片 | `pages/Cart.jsx`（Select → 卡片列表，channelCode 状态） | `views/Cart.vue`（el-select → 卡片列表，避免可选链 `?.`） |
| 内联色清理 | `pages/Cart.jsx` | `views/Cart.vue` |
| 其余页面 | Home/Orders/Account/Login/Success/AppHeader/AppFooter 零 JSX 改动，纯 CSS 承载 | 对应 views 零模板改动（Cart.vue 除外） |

新增共用 class（双端同名同规则）：`.m-pay-label/.m-pay-list/.m-pay-item(.active)/.m-pay-logo(.wx/.ali)/.m-pay-info/.m-pay-name/.m-pay-desc/.m-pay-check`。

## 3. API 契约与兼容性影响

| 契约 | 变更 | 兼容性 |
|---|---|---|
| `POST /api/checkout/orders` 请求体 | 不变（`paymentType` + `paymentAppId` + 收货信息） | 完全兼容 |
| `GET /api/payment-app/list`（启用支付应用） | 不变，前端按 `channelCode` 去重为渠道列表 | 完全兼容 |
| 支付渠道展示逻辑 | 变更：渠道级（去重）替代应用级下拉；同渠道多应用时默认取第一个启用应用 | 行为变更，已在本 spec 声明；单渠道多应用场景由管理端控制启停 |
| 路由/组件库/依赖 | 不变，不引入新依赖 | 完全兼容 |

## 4. 验收标准

- [x] 新 token 生效：主色橙红 `#fa5416`，无粉色系残留（rg 验证 `#ff5f8f|#ffe0ea` 为 0 命中，taobao.css 保留文件除外）✓ 2026-09-06
- [x] 结构型属性核对带齐：`#app` flex、`.m-bar` sticky、`.m-tabbar` fixed+flex、`.tb-grid` display:grid、`.tb-card-img` aspect-ratio+object-fit+overflow、`.tb-cart-row` order 排布、`.qr-frame` 结构（rg 逐项核对双端一致）✓ 2026-09-06
- [x] 无 `Npx Npx 0` 硬阴影残留（rg 命中仅为 margin/padding 数值，非 box-shadow）；卡片/列表/输入 hairline 分隔 ✓ 2026-09-06
- [x] 购物车出现「支付方式」渠道卡片列表，可单选高亮，未选禁用结算（React `disabled={!canCheckout}` / Vue `:disabled="!canCheckout"`）；结算仍按渠道映射启用应用下单（契约不变）✓ 2026-09-06
- [x] React 端内联硬编码杂色清理；Vue 端对等清理 ✓ 2026-09-06
- [x] React `npm run build` ✓（vite `✓ built`）；Vue `npm run build` ✓（vue-cli `DONE Build complete`，串行执行，仅既有 chunk 大小警告）✓ 2026-09-06
- [x] spec 状态/验收项/Change Log 更新并移入 `spec/implemented/frontend/` ✓ 2026-09-06
- [x] 登录/注册页重做（用户反馈「登录注册页过于丑陋」）：橙红渐变品牌 Hero（圆角方「淘」logo + 应用名 + slogan + 柔光斑）+ 悬浮白卡 + 药丸分段开关（登录/免费注册，替代 antd Tabs / el-tabs 下划线）+ 大号圆角输入框（emoji 前缀字形，双端一致）+ 渐变药丸主 CTA + 忘记密码/注册提示；新增共用 class `.tb-auth-*`（React `pages/Login.jsx`、Vue `views/Login.vue` 双端对等；`assets/css/mobile.css` 双端同名规则；Vue 侧 `.tb-auth-form .tb-auth-submit.el-button` 提特异性压过 Element 默认 `.el-button--primary`）；React vite build ✓ + Vue vue-cli build ✓（串行，仅既有 chunk 大小警告）✓ 2026-09-06

## 5. Change Log

| 日期 | 变更 |
|---|---|
| 2026-09-06 | 初版：精致现代风 token 体系 + 支付渠道卡片化选择，双端对等改造。 |
| 2026-09-06 | implemented：双端 mobile.css 主题重写（token/换肤段）、Cart 支付渠道卡片列表落地；rg 验收全过；React vite build ✓ + Vue vue-cli build ✓（串行）；归档至 `spec/implemented/frontend/`。 |
| 2026-09-06 | implemented：登录/注册页重做（延续精致现代风）——品牌渐变 Hero + 悬浮白卡 + 药丸分段开关 + 渐变药丸 CTA，新增双端共用 `.tb-auth-*`（React `pages/Login.jsx` / Vue `views/Login.vue` + 双端 mobile.css）；登录/注册/忘记密码交互与 API 契约不变，注册成功自动切回登录；React vite build ✓ + Vue vue-cli build ✓（串行）。 |
| 2026-09-06 | implemented：微信扫码支付弹窗新增「我已支付，查询支付结果」按钮（用户反馈二维码处需主动查单并提示成功）。复用既有归属校验接口 `GET /api/checkout/orders/{orderNo}`（OrderDetailVO.order.payStatus，与 3s 自动轮询同一数据路径，无后端/契约变更）：已支付→`支付成功` 提示 + 关窗 + 刷新列表，未支付→友好 info 提示稍后重试。锚点：React `pages/OrdersV2.jsx`（payQuerying 状态 + queryPayResult）、Vue `views/Orders.vue`（wxOrderNo/payQuerying + queryPayResult，Vue 无自动轮询故按钮为主要查单入口）；React vite build ✓ + Vue vue-cli build ✓（串行，仅既有 chunk 大小警告）。 |
