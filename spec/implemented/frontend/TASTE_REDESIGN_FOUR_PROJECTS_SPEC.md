# taste-skill 四工程品味重设计（Taste Refinement）Spec

- Date: 2026-09-05
- Status: implemented
- Domain: frontend
- Class: Design change（视觉品味精修，纯 UI 层，路由/API/交互契约不变）

## 1. Background

- 上一轮已完成四工程拆分与商城淘宝化；用户要求按 `taste-skill`（UI 设计品味技能）对 4 个工程整体重设计提质感。

## 2. Contract（taste-skill 六原则落地）

### 2.1 商城双端（payment-demo-react / payment-demo-vue）

- **字体系统**：全局 font stack（system-ui / PingFang SC / Microsoft YaHei）；金额/单价/小计/合计 `tabular-nums`。
- **细节打磨**：所有可交互元素 `:focus-visible` 焦点环；过渡保持 150-300ms。
- **状态完整性**：购物车空态（白卡 Empty + 引导文案，隐藏结算条/收货表单）；首页/列表空态已有。
- **一致性**：我的订单 / 我的退款 / 用户中心 / 支付成功统一 `tb-page + container + tb-h2 + tb-cardbox`（白卡容器）页面骨架，与首页/购物车一致。
- **页脚重设计**：旧深色「尚硅谷」页脚改为与商城身份一致的极简页脚（`tb-footer`：居中、两级小字）。

### 2.2 管理端双端（payment-demo-react-admin / payment-demo-vue-admin）

- **主题 token**：React `ConfigProvider`（fontFamily、borderRadius 6）；Vue `admin.css`（font-family、表头底色、按钮圆角统一）。
- **新 admin.css**：`.adm-page-title`（左侧 4px 蓝色强调条页标题）+ `.adm-card`（白卡、圆角 8、轻阴影、20px 内距）+ 表头 `#FAFAFA`。
- **一致性**：全部 12 个菜单页面统一骨架「adm-page-title + adm-card」：
  - AdminOrders/Shipping/Products/Refunds/MqLogs/ResetRequests/ResetPassword/UserList/StockMaintenance：补页标题（此前无标题）+ 表格/表单卡片化。
  - Download/PaymentConfig/Reconciliation：废弃旧 `comm-title/bg-fa` 绿色风头（global.css 遗留 #68cb9b 强调色，与后台气质不符），切换为 adm-page-title + adm-card。
- **AdminLayout**：顶栏加投影层次；字体统一。

### 2.3 不变项

- 路由、API 调用、业务逻辑、组件库版本、交互行为全部不变；仅视觉层（CSS/className/主题 token/少量 JSX 包裹层）。

## 3. Acceptance Criteria

- [x] 商城双端：字体/等宽数字生效；购物车空态；订单/退款/账户/成功页白卡骨架；新页脚；焦点态。
- [x] 管理端双端：12 页统一「页标题 + 白卡」骨架，无绿色旧样式残留；主题 token 生效。
- [x] 4 工程 `npm run build` 通过（2026-09-06 验证）。

## 4. Compatibility / Rollback

- 纯视觉变更，无契约影响；回滚 = git 还原本次提交。

## 5. Implementation Anchors

- 商城：`payment-demo-{react,vue}/src/assets/css/taobao.css`、`components/AppFooter.{jsx,vue}`、`pages/Cart.jsx`、`views/Cart.vue`、订单/退款/账户/成功页包裹层
- 管理端：`payment-demo-{react,vue}-admin/src/assets/css/admin.css`（新）、`main.jsx`/`App.vue`、AdminLayout、12 个页面骨架、Download/PaymentConfig/Reconciliation 头部样式

## 6. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-09-05 | planned | 初版：taste-skill 六原则在四工程的落地清单。 | User request: 使用 taste-skill 重新设计四工程 |
| 2026-09-06 | implemented | 四工程落地完成：商城双端（tb-page 骨架统一/空态/页脚/字体/焦点态/账户头像/成功页重设计）；管理端双端（admin.css token + adm-page-title/adm-card 统一 12 页 + Element 主色收敛 + 清除 bg-fa/comm-title 遗留）。4 工程 build 通过。 | 同上 |
