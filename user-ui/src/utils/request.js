import axios from 'axios'
import { message } from 'antd'
import { clearAuth, getAccessToken, getRefreshToken, saveAuth } from './authStore'
import { createRefreshSingleFlight } from './refreshSingleFlight'

// 生产构建未注入 VITE_API_BASE_URL 时使用同源（由 Nginx 反向代理 /api），避免打到访问者本机 localhost
const baseURL = import.meta.env.VITE_API_BASE_URL ?? (import.meta.env.DEV ? 'http://localhost:8080' : '')
const service = axios.create({ baseURL, timeout: 20000 })
const raw = axios.create({ baseURL, timeout: 20000 })

const refreshOnce = createRefreshSingleFlight(async () => {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new Error('Refresh Token 不存在')
  const response = await raw.post('/api/auth/refresh', { refreshToken })
  const payload = response.data
  if (!payload || payload.code !== 0 || !payload.data?.accessToken) throw new Error(payload?.message || '刷新登录失败')
  saveAuth(payload.data)
  return payload.data.accessToken
})

service.interceptors.request.use((config) => {
  const token = getAccessToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

service.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res && typeof res.code === 'number' && res.code !== 0) {
      message.error(res.message || '请求失败')
      return Promise.reject(res)
    }
    return res
  },
  async (error) => {
    const original = error.config || {}
    if (error?.response?.status === 401 && !original.__retried && !original.url?.includes('/api/auth/refresh') && !original.url?.includes('/api/auth/login') && !original.url?.includes('/api/auth/register')) {
      original.__retried = true
      const currentToken = getAccessToken()
      const failedToken = (original.headers?.Authorization || original.headers?.authorization || '').replace(/^Bearer\s+/i, '')
      // 并发请求的 401 可能晚到：若本地 Token 已被其它请求刷新，直接用新 Token 重放，避免二次消耗 Refresh Token
      if (currentToken && failedToken && currentToken !== failedToken) {
        original.headers = original.headers || {}
        original.headers.Authorization = `Bearer ${currentToken}`
        return service(original)
      }
      try {
        const token = await refreshOnce()
        original.headers = original.headers || {}
        original.headers.Authorization = `Bearer ${token}`
        return service(original)
      } catch (refreshError) {
        clearAuth()
        if (location.hash !== '#/login') location.hash = '#/login'
        return Promise.reject(refreshError)
      }
    }
    message.error(error?.response?.data?.message || error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default service
