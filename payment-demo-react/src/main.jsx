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

// 手机 App 动漫风：粉色主色 + 大圆角贯穿 antd 组件
ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#ff5f8f', colorLink: '#f0447c', borderRadius: 12 } }}>
      <App />
    </ConfigProvider>
  </React.StrictMode>
)
