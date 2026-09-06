// 商城端会话存储（与管理后台隔离，避免同源时相互覆盖登录态）
const PREFIX = 'mall_'
const ACCESS_KEY = PREFIX + 'access_token'
const REFRESH_KEY = PREFIX + 'refresh_token'
const USER_KEY = PREFIX + 'user'

// 一次性迁移：优先承接上一版隔离前缀 payment_demo_mall_*（角色已归属）；
// 再承接最初版共享前缀 payment_demo_*（按角色判断，管理员登录态归管理端，不迁移）
;(function migrateLegacyAuth() {
  if (localStorage.getItem(ACCESS_KEY)) return
  let token = localStorage.getItem('payment_demo_mall_access_token')
  let refresh = localStorage.getItem('payment_demo_mall_refresh_token')
  let userRaw = localStorage.getItem('payment_demo_mall_user')
  if (!token) {
    token = localStorage.getItem('payment_demo_access_token')
    refresh = localStorage.getItem('payment_demo_refresh_token')
    userRaw = localStorage.getItem('payment_demo_user')
    if (token) {
      let legacyUser = null
      try { legacyUser = JSON.parse(userRaw || 'null') } catch (e) { legacyUser = null }
      if (!legacyUser || legacyUser.role === 'ROLE_ADMIN') return
    }
  }
  if (!token) return
  localStorage.setItem(ACCESS_KEY, token)
  if (refresh) localStorage.setItem(REFRESH_KEY, refresh)
  if (userRaw) localStorage.setItem(USER_KEY, userRaw)
})()

export function getAccessToken() { return localStorage.getItem(ACCESS_KEY) || '' }
export function getRefreshToken() { return localStorage.getItem(REFRESH_KEY) || '' }
export function getUser() { try { return JSON.parse(localStorage.getItem(USER_KEY) || 'null') } catch (e) { return null } }
export function saveAuth(data) {
  if (data && data.accessToken) localStorage.setItem(ACCESS_KEY, data.accessToken)
  if (data && data.refreshToken) localStorage.setItem(REFRESH_KEY, data.refreshToken)
  if (data && data.user) localStorage.setItem(USER_KEY, JSON.stringify(data.user))
  window.dispatchEvent(new Event('payment-auth-changed'))
}
export function clearAuth() {
  localStorage.removeItem(ACCESS_KEY); localStorage.removeItem(REFRESH_KEY); localStorage.removeItem(USER_KEY)
  window.dispatchEvent(new Event('payment-auth-changed'))
}
