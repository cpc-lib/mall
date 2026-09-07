import { Navigate, useLocation } from 'react-router-dom'
import { getAccessToken } from '@/utils/authStore'

export function RequireAuth({ children }) {
  const location = useLocation()
  if (!getAccessToken()) return <Navigate to="/login" replace state={{ from: location.pathname }} />
  return children
}
