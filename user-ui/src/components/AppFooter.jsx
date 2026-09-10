import { NavLink } from 'react-router-dom'
import UiIcon from './UiIcon.jsx'

const tabs = [
  { to: '/', icon: 'home', label: '首页' },
  { to: '/cart', icon: 'cart', label: '购物车' },
  { to: '/orders', icon: 'box', label: '订单' },
  { to: '/account', icon: 'user', label: '我的' }
]
export default function AppFooter() {
  return (
    <nav className="m-tabbar">
      {tabs.map(t => (
        <NavLink key={t.to} to={t.to} end={t.to === '/'}
          className={({ isActive }) => 'm-tab' + (isActive ? ' active' : '')}>
          <span className="ico"><UiIcon name={t.icon} size={21} /></span>{t.label}
        </NavLink>
      ))}
    </nav>
  )
}
