# user-ui — 用户商城前端

用户商城单页应用（React 18 + Vite 5 + Ant Design 5），移动 App 风格界面，开发端口 `3000`，通过 CORS 直连后端
`http://localhost:8080`。

## 功能概览

- 商品浏览与搜索、商品详情
- 购物车（实时读取最新价格与库存）
- 收货地址管理（三级行政区划级联 + 默认地址）
- 下单结算（库存预占）、扫码支付（微信 Native 二维码轮询、支付宝表单跳转）
- 订单列表/详情、物流时间线、确认收货、取消订单
- 分项退款申请（未发货取消/退货退款/仅退款/差价，待审核可编辑/撤销）
- 用户中心：登录注册、修改资料、修改密码（弹窗）、找回密码申请
- 双 Token 无感刷新（401 单飞刷新）

## 命令

```bash
npm install          # 安装依赖（Node.js 18+）
npm run dev          # 启动开发环境 → http://localhost:3000
npm run build        # 生产构建
npm run test:logic   # 逻辑单测（Token 单飞刷新、退款额度核算）
```

## 后端接口地址

默认请求 `http://localhost:8080`，配置见 `src/utils/request.js` 的 baseURL。

## 相关文档

- 项目总览与快速启动：[../README.md](../README.md)
- 架构与代码导览：[../CODE_INTRO.md](../CODE_INTRO.md)
