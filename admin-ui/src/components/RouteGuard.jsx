import { Navigate, useLocation } from 'react-router-dom'
import { clearAuth, getAccessToken, getUser } from '@/utils/authStore'

export function RequireAuth({ children }) {
  const location = useLocation()
  if (!getAccessToken()) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

export function RequireAdmin({ children }) {
  const location = useLocation()
  if (!getAccessToken()) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (getUser()?.role !== 'ROLE_ADMIN') {
    // 非管理员 Token 直接清除并回登录页，避免 / ↔ /admin/orders 相互重定向死循环
    clearAuth()
    return <Navigate to="/login" replace />
  }
  return children
}
