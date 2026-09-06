import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppHeader from './components/AppHeader.jsx'
import AppFooter from './components/AppFooter.jsx'
import Home from './pages/Home.jsx'
import Success from './pages/Success.jsx'
import Login from './pages/Login.jsx'
import Cart from './pages/Cart.jsx'
import OrdersV2 from './pages/OrdersV2.jsx'
import Account from './pages/Account.jsx'
import RefundApplications from './pages/RefundApplications.jsx'
import { RequireAuth } from './components/RouteGuard.jsx'

const auth = (node) => <RequireAuth>{node}</RequireAuth>

// 用户商城（淘宝风格）：管理后台已拆分至独立工程 payment-demo-react-admin
export default function App() {
  return (
    <HashRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <div id="app">
        <AppHeader />
        <main className="app-main">
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/success" element={<Success />} />
          <Route path="/cart" element={auth(<Cart />)} />
          <Route path="/orders" element={auth(<OrdersV2 />)} />
          <Route path="/orders-v2" element={<Navigate to="/orders" replace />} />
          <Route path="/refund-applications" element={auth(<RefundApplications />)} />
          <Route path="/account" element={auth(<Account />)} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </main>
        <AppFooter />
      </div>
    </HashRouter>
  )
}
