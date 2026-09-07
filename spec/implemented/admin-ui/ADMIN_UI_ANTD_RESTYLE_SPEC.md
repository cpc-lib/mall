# Admin UI Ant Design 原生风格改造 SPEC

- Status: implemented
- 日期: 2026-09-07
- 范围: admin-ui（管理后台单端；用户商城 user-ui 不在本次范围）
- 分类: Design change（主题/组件呈现层重构，无路由/API/契约变更）

## 背景与目标

admin-ui 当前为 antd Layout 骨架 + 自定义风格层（`.adm-page-title` 蓝条标题、`.adm-card` 自绘卡片、`.adm-toolbar`、`--adm-*` 变量），并从 user-ui 拷贝了两份死 CSS（`theme.css` 绿色商城主题、`global.css` 商城页样式，admin 页面零引用）。

目标：管理后台呈现为标准 Ant Design 风格——页面标题用 `Typography.Title`、卡片用 antd `Card`、去掉自绘装饰与死样式文件；布局（Header/Sider/Menu）、路由、API、交互逻辑零变更。

## 改动项（实施锚点）

1. 12 个页面（`admin-ui/src/pages/`）：
   - `<h2 className="adm-page-title">X</h2>` → `<Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>X</Typography.Title>`
   - `<div className="adm-card">…</div>` → `<Card>…</Card>`；原 `style={{ padding: '8px 20px 16px' }}` 迁移为 `styles={{ body: { padding } }}`
   - Download/StockMaintenance 的 `adm-card-head/title/sub` → Card 的 `title`/`extra` 属性 + `Typography.Text type="secondary"`（长说明文字进卡片正文 `Typography.Paragraph`）
2. AdminLayout.jsx 顶栏文案 `支付业务演示 · 管理后台` → `电商商城平台 · 管理后台`
3. main.jsx：移除 `theme.css`、`global.css` 死 CSS import（保留 reset.css、admin.css）
4. admin.css 精简为三条规则：focus-visible 可达性、`.ant-card + .ant-card` 堆叠间距（替代原 `.adm-card + .adm-card`）、表格数字等宽对齐

## 不改动

- 路由结构、API 请求/响应、组件状态逻辑、`request.js`/`authStore.js`、Login/StockExcelEditor 页面（无 adm 类）
- ConfigProvider（borderRadius 6 = antd v5 默认；fontFamily 中文栈保留以满足按钮字体清晰要求）

## 验收标准

- [x] 12 个页面无 `adm-` 类残留（grep 0 处）
- [x] AdminLayout 顶栏文案为「电商商城平台 · 管理后台」
- [x] main.jsx 不再 import theme.css / global.css；admin.css 无死规则
- [x] `npm run build`（admin-ui）通过（vite build ✓ 3.68s，2026-09-07）
- [x] `npm run test:logic`（admin-ui）通过（1 pass / 0 fail，2026-09-07）
- [x] 全局 grep 无「支付业务演示」残留（0 处）

## 兼容性 / 回滚

- 无公共 API、路由、数据契约变更；纯呈现层
- 回滚：git 还原 `admin-ui/src/`（pages/main.jsx/admin.css/AdminLayout.jsx）与 spec 文件即可

## Change Log

| 日期 | 状态 | 摘要 |
|---|---|---|
| 2026-09-07 | implemented | 12 页面 adm-page-title→Typography.Title、adm-card→Card；AdminLayout 品牌文案统一；main.css 死 CSS 移除；admin.css 精简。vite build ✓ / test:logic 1 pass 0 fail |
| 2026-09-07 | implemented | 亮色化微调：Header 深藏青→白底+主色装饰点、Content #F5F6F8→#e6f4ff、Login 深底→浅蓝底+柔和阴影、全局圆角 6→8（AdminLayout/Login/main.jsx token）。vite build ✓ 3.93s / test:logic 1 pass 0 fail |
