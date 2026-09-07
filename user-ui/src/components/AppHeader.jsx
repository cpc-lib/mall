import { Link, useNavigate } from 'react-router-dom'
import { getAccessToken, getUser } from '@/utils/authStore'

export default function AppHeader() {
  const navigate = useNavigate()
  const user = getUser()
  const loggedIn = Boolean(getAccessToken())
  return (
    <header className="m-bar">
      <div className="m-bar-row">
        <Link to="/" className="m-logo">
          <span>🛍️</span>
          <span className="m-logo-text">Mall</span>
        </Link>
        {loggedIn
          ? <Link to="/account" className="m-user">👋 {user?.username || '我的'}</Link>
          : <Link to="/login" className="m-user">登录 / 注册</Link>}
      </div>
      <div className="m-search">
        <input placeholder="搜索好物（演示入口，回车逛全店）" onKeyDown={e => { if (e.key === 'Enter') navigate('/') }} />
        <button onClick={() => navigate('/')}>搜索</button>
      </div>
    </header>
  )
}
