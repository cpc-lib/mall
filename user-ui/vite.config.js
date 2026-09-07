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
          if (/[\\/]node_modules[\\/]qrcode\.react[\\/]/.test(id)) return
          return 'vendor-antd'
        }
      }
    }
  },
})
