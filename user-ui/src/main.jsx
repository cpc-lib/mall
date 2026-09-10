window.addEventListener('error', ev => {
  const src = ev.filename || ''
  if (/^VM\d+/.test(src) || (ev.message && /startTime|reportAllChanges|isComposing/.test(ev.message))) {
    ev.stopImmediatePropagation()
    ev.preventDefault()
  }
}, true)

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
import './assets/css/polish.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider locale={zhCN} theme={{ token: { colorPrimary: '#ff5a36', colorLink: '#e64524', borderRadius: 12, fontFamily: 'Inter, ui-sans-serif, -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif' } }}>
      <App />
    </ConfigProvider>
  </React.StrictMode>
)
