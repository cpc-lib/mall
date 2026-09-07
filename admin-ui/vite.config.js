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
          if (/[\\/]node_modules[\\/](xlsx|qrcode\.react)[\\/]/.test(id)) return
          return 'vendor-antd'
        }
      }
    }
  },
})
