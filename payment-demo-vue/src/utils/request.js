import axios from 'axios'
import { Message } from 'element-ui'
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
  if (!payload || payload.code !== 0 || !payload.data || !payload.data.accessToken) throw new Error((payload && payload.message) || '刷新登录失败')
  saveAuth(payload.data)
  return payload.data.accessToken
})

service.interceptors.request.use(config => {
  const token = getAccessToken()
  if (token) { config.headers = config.headers || {}; config.headers.Authorization = `Bearer ${token}` }
  return config
})
service.interceptors.response.use(response => {
  const res = response.data
  if (res && typeof res.code === 'number' && res.code !== 0) {
    Message.error(res.message || '请求失败')
    return Promise.reject(res)
  }
  return res
}, async error => {
  const original = error.config || {}
  const url = original.url || ''
  if (error.response && error.response.status === 401 && !original.__retried && url.indexOf('/api/auth/refresh') < 0 && url.indexOf('/api/auth/login') < 0 && url.indexOf('/api/auth/register') < 0) {
    original.__retried = true
    try {
      const token = await refreshOnce()
      original.headers = original.headers || {}; original.headers.Authorization = `Bearer ${token}`
      return service(original)
    } catch (refreshError) {
      clearAuth()
      if (window.location.hash !== '#/login') window.location.hash = '#/login'
      return Promise.reject(refreshError)
    }
  }
  Message.error((error.response && error.response.data && error.response.data.message) || error.message || '网络异常')
  return Promise.reject(error)
})
export default service
