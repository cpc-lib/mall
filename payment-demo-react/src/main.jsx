import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import 'antd/dist/reset.css'

import App from './App.jsx'
import './assets/css/reset.css'
import './assets/css/theme.css'
import './assets/css/global.css'
import './assets/css/mobile.css'

// 手机 App 精致现代主题：橙红主色 + 统一圆角贯穿 antd 组件
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#fa5416', colorLink: '#d4380d', borderRadius: 10 } }}>
      <App />
    </ConfigProvider>
  </React.StrictMode>
)
