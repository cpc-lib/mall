import { Suspense, lazy } from 'react'
import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
import AdminLayout from './components/AdminLayout.jsx'
import { RequireAdmin } from './components/RouteGuard.jsx'

const Login = lazy(() => import('./pages/Login.jsx'))
const AdminOrders = lazy(() => import('./pages/AdminOrders.jsx'))
const AdminShipping = lazy(() => import('./pages/AdminShipping.jsx'))
const AdminProducts = lazy(() => import('./pages/AdminProducts.jsx'))
const AdminRefunds = lazy(() => import('./pages/AdminRefunds.jsx'))
const AdminResetRequests = lazy(() => import('./pages/AdminResetRequests.jsx'))
const AdminResetPassword = lazy(() => import('./pages/AdminResetPassword.jsx'))
const StockMaintenance = lazy(() => import('./pages/StockMaintenance.jsx'))
const StockExcelEditor = lazy(() => import('./pages/StockExcelEditor.jsx'))
const UserList = lazy(() => import('./pages/UserList.jsx'))
const Download = lazy(() => import('./pages/Download.jsx'))
const PaymentConfig = lazy(() => import('./pages/PaymentConfig.jsx'))
const Reconciliation = lazy(() => import('./pages/Reconciliation.jsx'))

const admin = (node) => <RequireAdmin>{node}</RequireAdmin>

const pageFallback = <div style={{ padding: '48px 0', textAlign: 'center', color: '#909399' }}>加载中…</div>

export default function App() {
  return (
    <HashRouter>
      <Suspense fallback={pageFallback}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/admin" element={admin(<AdminLayout />)}>
          <Route index element={<Navigate to="/admin/orders" replace />} />
          <Route path="orders" element={<AdminOrders />} />
          <Route path="shipping" element={<AdminShipping />} />
          <Route path="products" element={<AdminProducts />} />
          <Route path="refunds" element={<AdminRefunds />} />
          <Route path="users" element={<UserList />} />
          <Route path="reset-requests" element={<AdminResetRequests />} />
          <Route path="reset-password" element={<AdminResetPassword />} />
          <Route path="stock-maintenance" element={<StockMaintenance />} />
          <Route path="download" element={<Download />} />
          <Route path="payment-config" element={<PaymentConfig />} />
          <Route path="reconciliation" element={<Reconciliation />} />
        </Route>
        {/* 全屏 Excel 编辑器（新页签打开，铺满整页） */}
        <Route path="/admin/stock-edit/:id" element={admin(<StockExcelEditor />)} />
        <Route path="/" element={<Navigate to="/admin/orders" replace />} />
        <Route path="*" element={<Navigate to="/admin/orders" replace />} />
      </Routes>
      </Suspense>
    </HashRouter>
  )
}
