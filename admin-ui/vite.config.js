import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 3002,
    host: '0.0.0.0'
  },
  build: {
    chunkSizeWarningLimit: 1200,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (/[\\/]node_modules[\\/](react|react-dom|react-router|@remix-run|scheduler|use-sync-external-store)[\\/]/.test(id)) return 'vendor-react'
          // icons 依赖闭包(icons/icons-svg/colors/fast-color + 运行时底座 @babel/runtime/classnames/rc-util/react-is)同块：
          // antd 反向引用 icons，若其依赖散落 vendor-antd 会形成块间循环，故整链同块保证单向依赖
          if (/[\\/]node_modules[\\/](@ant-design[\\/](icons|icons-svg|colors|fast-color)|@babel[\\/]runtime|classnames|rc-util|react-is)[\\/]/.test(id)) return 'vendor-antd-icons'
          if (/[\\/]node_modules[\\/](xlsx|qrcode\.react)[\\/]/.test(id)) return
          return 'vendor-antd'
        }
      }
    }
  },
})
