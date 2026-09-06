# 用户商城移动端列表卡片化与布局溢出修复 Spec（payment-demo-react / payment-demo-vue）

- Date: 2026-09-06
- Status: planned
- Domain: frontend（用户商城两端：payment-demo-react、payment-demo-vue；管理后台不在范围）
- Class: Design change（列表呈现形态由桌面表格改为移动卡片；路由、API、请求响应结构、业务状态判断均不变）

## 1. 背景与问题

手机 App 动漫风改造（MALL_MOBILE_ANIME_STYLE_SPEC）将页面收进 480px 手机壳，但订单/退款页仍沿用桌面端宽表格，留下系统性布局缺陷：

1. **React 订单页表格挤压截断（用户截图反馈）**：`OrdersV2.jsx` 的 antd Table 未设 `scroll.x`，6 列在 ~440px 内容宽内被压成单字竖排（订单号 ORD2026... 一字一行），300px 操作列溢出卡片被裁切，「物流详情 / 分项退款」等按钮不可见、不可点。
2. **移动端表格形态本身不适配**：React 退款页 Table 靠 `scroll.x=860` 横滚、Vue 订单/退款 el-table（约 1150px）靠自动横滚，关键信息与操作按钮不能同屏，需左右拖拽。
3. **React mobile.css 换肤时丢失结构属性（Vue 端正常）**：
   - `.tb-receiver` 缺 `display:flex; flex-wrap:wrap`，`gap:10px` 失效，收货信息输入框纵向堆叠无间距；
   - `.tb-account-head` 缺 flex 布局、`.tb-account-avatar` 缺尺寸/居中、`.tb-account-name/.tb-account-role` 完全缺失，用户中心头像无大小、用户名/角色无排版。
4. **弹窗窄屏溢出**：antd `Radio.Group` 为 inline-flex 不换行，退款弹窗长选项（「退货退款（签收质检后补库存）」）在 440px 弹窗内溢出；弹窗商品行（标题 + InputNumber + 提示文案）自然排布，长标题挤压数字输入框。

## 2. 目标模式（验收标准）

### 2.1 订单/退款列表卡片化（双端对等）
- 桌面表格替换为**纵向卡片列表**，每张订单/退款一张卡片，class 双端统一：
  - `.m-list-card`：白底、18px 圆角、1.5px 粉描边、柔和阴影，卡片间距 12px；
  - `.m-list-head`：单号（12px 弱化、`word-break:break-all`、等宽数字）与状态标签区左右分布、可换行；
  - `.m-list-sub`：支付方式/类型（弱化）与金额（17px 加粗主色、tabular-nums）同行两端对齐；
  - `.m-list-items`：虚线上分隔，商品明细 12.5px / 行高 1.7，快照价、已退/冻结数量弱化为副信息；失败原因红色；
  - `.m-list-actions`：虚线上分隔，按钮 `flex-wrap` 自动换行、`flex:1 1 auto; min-width:96px`，所有操作在卡片内完整可见可点。
- 空态保留：React 用 antd `Empty` 卡片，Vue 用等宽文案卡（`.m-list-empty`）。
- 列内全部业务判断（canPay/canCancel/canLogistics/canConfirm/canRefund、退款状态等）与数据来源不变，仅改呈现容器。

### 2.2 React 结构样式补齐（与 Vue mobile.css 对齐）
- `.tb-receiver` 补 `display:flex; flex-wrap:wrap`（恢复 column 排布与 gap 间距）。
- `.tb-account-head` 补 `display:flex; align-items:center; gap:12px; padding-bottom:16px; margin-bottom:20px`；
- `.tb-account-avatar` 补 48px 圆形容器与居中排版；新增 `.tb-account-name`（16px/700）、`.tb-account-role`（12px 弱化）。

### 2.3 弹窗窄屏适配
- 退款弹窗 Radio 组换行：antd `.ant-modal .ant-radio-group { display:flex; flex-wrap:wrap; gap:4px 12px }`；Element `.el-dialog .el-radio` 右边距收窄并自然换行。
- 弹窗商品行统一 `.m-modal-line`：`display:flex; flex-wrap:wrap; align-items:center; gap:6px 8px`，长标题换行而不挤压 InputNumber（双端订单退款弹窗、退款编辑弹窗共 4 处）。

## 3. 兼容性

- 纯前端改造：不改路由路径、API、组件库版本、请求/响应结构、业务状态机。
- 仅新增/修改 CSS class 与列表渲染容器；旧 `tb-*` class、`taobao.css` 保留不删。
- 回滚 = git 还原本次提交。

## 4. 实施锚点

- React（payment-demo-react/src）：
  - `pages/OrdersV2.jsx`：Table → `.m-list-card` 卡片列表；退款弹窗商品行改 `.m-modal-line`；清理 Table/Space 未用 import
  - `pages/RefundApplications.jsx`：Table → 卡片列表；编辑弹窗商品行改 `.m-modal-line`；引入 Empty 空态；清理未用 import
  - `assets/css/mobile.css`：新增 `.m-list-*` / `.m-modal-line` 卡片与弹窗样式；补 `.tb-receiver` / `.tb-account-*` 结构属性；Radio 组换行
- Vue（payment-demo-vue/src）：
  - `views/Orders.vue`：el-table → `.m-list-card` 卡片模板；退款弹窗商品行改 `.m-modal-line`
  - `views/RefundApplications.vue`：el-table → 卡片模板；编辑弹窗商品行改 `.m-modal-line`；空态文案卡
  - `assets/css/mobile.css`：新增对等 `.m-list-*` / `.m-modal-line` 样式；Radio 间距收窄换行

## 5. 验收

- [ ] React 订单页 480px 下：订单号完整可读、状态标签/金额/商品明细完整展示，支付/取消/物流/确认收货/分项退款按钮全部可见可点、自动换行；`npm run build` 通过
- [ ] React 退款页卡片化对等呈现，失败原因红色完整可见；空态正常
- [ ] React 用户中心头像（48px 圆形）与用户名/角色排版正常；购物车收货信息输入框间距正常
- [ ] 双端退款弹窗：长 Radio 选项不溢出，商品行长标题换行不挤压 InputNumber
- [ ] Vue 订单/退款页对等卡片化；`npm run build` 通过
- [ ] 双端 CSS class 与规则逐条对齐；无横向溢出、无内容裁切

## 6. Change Log

| Date | Status | Change |
|---|---|---|
| 2026-09-06 | planned | 初版：订单/退款列表移动端卡片化 + React 结构样式补齐 + 弹窗窄屏适配 |
| 2026-09-06 | implemented | 双端落地：① React `OrdersV2.jsx` / `RefundApplications.jsx` 与 Vue `Orders.vue` / `RefundApplications.vue` 桌面表格改为 `.m-list-card` 卡片列表（单号 break-all、金额 tabular-nums、操作按钮 flex-wrap 全可见），空态分别用 antd Empty / `.m-list-empty`；② React `mobile.css` 补齐 `.tb-receiver` display:flex 与 `.tb-account-head/.tb-account-avatar/.tb-account-name/.tb-account-role` 结构属性（对齐 Vue）；③ 弹窗 Radio 组换行 + 商品行 `.m-modal-line` flex-wrap（双端 4 处弹窗）；④ 清理双端失效的 `.ant-table`/`.el-table` 换肤规则与未用 import。vite build ✓ / vue-cli build ✓（2026-09-06） |
