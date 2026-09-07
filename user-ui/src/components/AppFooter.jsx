import { NavLink } from 'react-router-dom'

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
