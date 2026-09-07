import { Suspense, lazy } from 'react'
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
import AppHeader from './components/AppHeader.jsx'
import AppFooter from './components/AppFooter.jsx'
import { RequireAuth } from './components/RouteGuard.jsx'

const Home = lazy(() => import('./pages/Home.jsx'))
const Login = lazy(() => import('./pages/Login.jsx'))
const Success = lazy(() => import('./pages/Success.jsx'))
const Cart = lazy(() => import('./pages/Cart.jsx'))
const OrdersV2 = lazy(() => import('./pages/OrdersV2.jsx'))
const Account = lazy(() => import('./pages/Account.jsx'))
const RefundApplications = lazy(() => import('./pages/RefundApplications.jsx'))
const Addresses = lazy(() => import('./pages/Addresses.jsx'))

const auth = (node) => <RequireAuth>{node}</RequireAuth>

const pageFallback = <div style={{ padding: '48px 0', textAlign: 'center', color: '#909399' }}>加载中…</div>

export default function App() {
  return (
    <HashRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <div id="app">
        <AppHeader />
        <main className="app-main">
        <Suspense fallback={pageFallback}>
        <Routes>
          <Route path="/" element={<Home />} />
          <Route path="/login" element={<Login />} />
          <Route path="/success" element={<Success />} />
          <Route path="/cart" element={auth(<Cart />)} />
          <Route path="/orders" element={auth(<OrdersV2 />)} />
          <Route path="/orders-v2" element={<Navigate to="/orders" replace />} />
          <Route path="/refund-applications" element={auth(<RefundApplications />)} />
          <Route path="/account" element={auth(<Account />)} />
          <Route path="/addresses" element={auth(<Addresses />)} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        </Suspense>
        </main>
        <AppFooter />
      </div>
    </HashRouter>
  )
}
