# 用户商城「手机 App 动漫风」视觉改造 Spec（payment-demo-react / payment-demo-vue）

- Date: 2026-09-06
- Status: planned
- Domain: frontend（用户商城两端：payment-demo-react、payment-demo-vue；管理后台不在范围）
- Class: Design change（纯视觉/布局改造；路由、API、请求响应结构不变）

## 1. 背景与问题

两个用户商城当前为桌面淘宝风格（三层通栏头部 + 1160px 容器 + 桌面表格/购物车横排），与「手机 App」使用形态不符；微信扫码支付二维码裸渲染、无收束。用户要求：

1. 改成手机 App 风格；
2. 支付二维码外面用一个方框包住；
3. 整体 UI 动漫风格。

## 2. 目标模式（验收标准）

### 2.1 手机壳布局
- 桌面访问时：`body` 为粉紫渐变装饰背景，`#app` 收窄为 **480px 居中「手机屏」**（奶油底色 + 两侧阴影），移动端访问时全屏铺满。
- 顶部 **App Bar**：粉→橙渐变、圆角下沿、sticky；左侧 🛍️ 品牌名，右侧用户chip/登录入口；下方白色半透明搜索药丸（保留现有「回车回首页」演示行为）。
- 底部 **Tab 栏**：fixed 居中、最大宽度 480px，4 个 Tab：🏠首页 / 🛒购物车 / 📦订单 / 👤我的；当前路由高亮。
- 页面内容底部留白，避免被 Tab 栏遮挡。

### 2.2 动漫视觉语言
- 主色 `#ff5f8f`（动漫粉），强调辅色紫 `#8b7cf6` 仅少量点缀；奶油底 `#fff6f1`，墨色 `#4c3f46`。
- 卡片大圆角（16–20px），关键卡片（商品卡/登录卡/二维码框）用 **2px 柔和描边 + 硬偏移卡通阴影**（如 `4px 4px 0 rgba(255,95,143,.25)`）。
- 按钮药丸化（radius 999），主按钮实心粉、加粗；交互有 hover/active 反馈。
- 组件库主题同步换肤：antd ConfigProvider token（colorPrimary/borderRadius）；Element UI 主色 CSS 覆盖。

### 2.3 二维码方框
- 微信扫码支付弹窗中，二维码外包 `.qr-frame`：白底、3px 主色描边、24px 圆角、padding 16px、卡通硬阴影，呈方形相框；二维码本身不变（React `QRCodeSVG` size 300 / Vue `qriously` size 300）。

### 2.4 页面适配（双端对等）
- 首页商品网格：2 列卡片。
- 购物车：隐藏桌面表头，每行改为卡片式纵向排布（勾选+图+标题在上，单价/数量/小计/删除在下自动换行）；收货信息输入框整行宽度；结算条 sticky 于 Tab 栏上方。
- 订单/退款表格：保留表格，卡片容器内可横向滚动（React Table `scroll.x`；el-table 超宽自动横滚）。
- 登录页：整屏粉紫渐变 + 圆角白卡 + 卡通阴影。
- 支付成功页：居中圆角卡片。
- 用户中心：新增「我的订单 / 我的退款申请」快捷入口卡（退款入口在 Tab 栏无独立位，由此进入），页脚演示声明小字移至该页。
- 弹窗（antd Modal / el-dialog）：圆角 18px，宽度收窄为 min(92vw, 440~460px) 以贴合手机屏。

## 3. 兼容性

- 纯前端改造：不改路由路径、不改 API、不改组件库版本。
- 既有 `tb-*` class 命名保留（新主题文件 `mobile.css` 覆盖其样式），`taobao.css` 停止引入但保留文件不删（回滚只需切回 import）。
- 回滚 = git 还原本次提交。

## 4. 实施锚点

- React（payment-demo-react/src）：
  - 新增 `assets/css/mobile.css`；`main.jsx` 换 import 并改 ConfigProvider token
  - `App.jsx`（main 包裹）、`components/AppHeader.jsx`（App Bar）、`components/AppFooter.jsx`（Tab 栏）
  - `pages/OrdersV2.jsx`（qr-frame + Table scroll.x）、`pages/RefundApplications.jsx`（Table scroll.x）、`pages/Account.jsx`（快捷入口）
- Vue（payment-demo-vue/src）：
  - 新增 `assets/css/mobile.css`；`App.vue` 换 import 并包裹 main
  - `components/AppHeader.vue`、`components/AppFooter.vue`
  - `views/Orders.vue`（qr-frame）、`views/Account.vue`（快捷入口）

## 5. 验收

- [ ] React：480px 手机壳 + App Bar + 底部 Tab 栏；二维码方框；动漫风配色/圆角/阴影；`npm run build` 通过
- [ ] Vue：对等实现；`npm run build` 通过
- [ ] 购物车/订单/登录/成功/用户中心页在 480px 宽度下无横向溢出、Tab 栏不遮挡内容

## 6. Change Log

| Date | Status | Change |
|---|---|---|
| 2026-09-06 | planned | 初版：双商城端手机 App 动漫风 + 二维码方框 |
| 2026-09-06 | implemented | 双端落地：mobile.css 主题（手机壳布局/渐变 App Bar/底部 Tab 栏/动漫卡片/二维码方框 .qr-frame）；antd token 与 Element 主色换肤；订单/退款表格卡片内横滚；用户中心加快捷入口与演示声明；双端 build 通过 |
| 2026-09-06 | implemented | 图片适配补丁：补回换肤时丢失的商品图约束——`.tb-card-img` 用 `aspect-ratio:1/1` 锁定方形图盒 + `img` 宽高 100%/object-fit:cover；购物车缩略图补 object-fit:cover/flex-shrink:0/底色，远程大图不再溢出或拉伸，随卡片宽度自适应（双端 mobile.css，build 通过） |
| 2026-09-06 | implemented | 图片显示异常根因修复（双端）：① React 端 `.tb-grid` 补回 `display:grid`（旧 taobao.css 提供，换肤时遗漏，导致商品卡退化为整行堆叠）；② 商品图盒改用 taobao 验证过的模式——`.tb-card` flex 列 + `overflow:hidden`，`.tb-card-img` 相对定位 `aspect-ratio:1/1`，img 绝对定位 `inset:0` + `object-fit:cover`，任何比例/竖版占位图都裁切在方盒内；③ `mallImgs.js` 新增内置 SVG data-URI 兜底图与 `onMallImgError`，远程图网络失败时（text_to_image 服务返回"生成中"竖版占位或不可达）自动替换，页面不破版；Home/Cart 共 4 处 img 接入；标题 2 行截断。双端 build 通过 |
| 2026-09-06 | implemented | 线上崩溃修复（React 商城）：Home.jsx / Cart.jsx 中 `onMallImgError` 的 import 丢失导致 `ReferenceError: onMallImgError is not defined`，首页渲染崩溃（Vite 构建不校验未定义标识符故 build 未拦截）；补回两个文件的 named import。另为 HashRouter 加 `future={{ v7_startTransition: true, v7_relativeSplatPath: true }}` 消除 React Router v7 迁移警告。双端 build 通过 |
