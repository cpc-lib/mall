import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider } from 'antd'
import zhCN from 'antd/locale/zh_CN'
import 'antd/dist/reset.css'

import App from './App.jsx'
import './assets/css/reset.css'
import './assets/css/admin.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={{ token: { fontFamily: "-apple-system, BlinkMacSystemFont, 'PingFang SC', 'Microsoft YaHei', 'Helvetica Neue', Arial, sans-serif", colorPrimary: '#1677ff', colorInfo: '#1677ff', borderRadius: 8 } }}>
      <App />
    </ConfigProvider>
  </React.StrictMode>
)
