import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getAccessToken, getUser } from '@/utils/authStore'
import UiIcon from './UiIcon.jsx'

export default function AppHeader() {
  const navigate = useNavigate()
  const user = getUser()
  const loggedIn = Boolean(getAccessToken())
  const [keyword, setKeyword] = useState('')
  const submitSearch = e => {
    e.preventDefault()
    const q = keyword.trim()
    navigate(q ? `/?q=${encodeURIComponent(q)}` : '/')
  }
  return (
    <header className="m-bar">
      <div className="m-bar-row">
        <Link to="/" className="m-logo">
          <span className="m-logo-mark"><UiIcon name="bag" size={21} /></span>
          <span className="m-logo-text">Mall</span>
        </Link>
        {loggedIn
          ? <Link to="/account" className="m-user"><UiIcon name="user" size={16} />{user?.username || '我的'}</Link>
          : <Link to="/login" className="m-user">登录 / 注册</Link>}
      </div>
      <form className="m-search" role="search" onSubmit={submitSearch}>
        <UiIcon name="search" size={18} className="m-search-icon" />
        <input value={keyword} onChange={e => setKeyword(e.target.value)} placeholder="搜索商品" aria-label="搜索商品" />
        <button type="submit" aria-label="搜索">搜索</button>
      </form>
    </header>
  )
}
