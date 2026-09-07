import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'node:path'

const projectRoot = path.resolve(__dirname)

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: {
      '@': projectRoot + '/src'
    }
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    // 严格文件系统访问：只允许项目根 + node_modules，禁止扫描 Trae IDE workspaceStorage 等外部目录
    fs: {
      strict: true,
      allow: [projectRoot, path.resolve(__dirname, 'node_modules')]
    },
    // 禁用文件系统 watcher 的全量扫描，避免扫到 IDE 缓存
    watch: {
      ignored: ['**/node_modules/**', '**/.git/**', '**/workspaceStorage/**']
    }
  },
  build: {
    chunkSizeWarningLimit: 700,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (/[\\/]node_modules[\\/](react|react-dom|react-router|@remix-run|scheduler|use-sync-external-store)[\\/]/.test(id)) return 'vendor-react'
          // icons 依赖闭包(icons/icons-svg/colors/fast-color + 运行时底座 @babel/runtime/classnames/rc-util/react-is)同块：
          // antd 反向引用 icons，若其依赖散落 vendor-antd 会形成块间循环，故整链同块保证单向依赖
          if (/[\\/]node_modules[\\/](@ant-design[\\/](icons|icons-svg|colors|fast-color)|@babel[\\/]runtime|classnames|rc-util|react-is)[\\/]/.test(id)) return 'vendor-antd-icons'
          if (/[\\/]node_modules[\\/]qrcode\.react[\\/]/.test(id)) return
          return 'vendor-antd'
        }
      }
    }
  },
})
