import { Navigate, useLocation } from 'react-router-dom'
import { getAccessToken } from '@/utils/authStore'

// 用户商城路由守卫：未登录跳登录页并记住来源
export function RequireAuth({ children }) {
  const location = useLocation()
  if (!getAccessToken()) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}
