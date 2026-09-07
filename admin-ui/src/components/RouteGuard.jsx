import { Navigate, useLocation } from 'react-router-dom'
import { getAccessToken, getUser } from '@/utils/authStore'

export function RequireAuth({ children }) {
  const location = useLocation()
  if (!getAccessToken()) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}

export function RequireAdmin({ children }) {
  const location = useLocation()
  if (!getAccessToken()) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  if (getUser()?.role !== 'ROLE_ADMIN') return <Navigate to="/" replace />
  return children
}
