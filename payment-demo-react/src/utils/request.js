import axios from 'axios'
import { message } from 'antd'
import { clearAuth, getAccessToken, getRefreshToken, saveAuth } from './authStore'
import { createRefreshSingleFlight } from './refreshSingleFlight'

const baseURL = 'http://localhost:8080'
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
