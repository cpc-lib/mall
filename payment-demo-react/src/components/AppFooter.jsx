import { NavLink } from 'react-router-dom'

// 手机 App 底部 Tab 栏：首页 / 购物车 / 订单 / 我的
const tabs = [
  { to: '/', ico: '🏠', label: '首页' },
  { to: '/cart', ico: '🛒', label: '购物车' },
  { to: '/orders', ico: '📦', label: '订单' },
  { to: '/account', ico: '👤', label: '我的' }
]
export default function AppFooter() {
  return (
    <nav className="m-tabbar">
      {tabs.map(t => (
        <NavLink key={t.to} to={t.to} end={t.to === '/'}
          className={({ isActive }) => 'm-tab' + (isActive ? ' active' : '')}>
          <span className="ico">{t.ico}</span>{t.label}
        </NavLink>
      ))}
    </nav>
  )
}
