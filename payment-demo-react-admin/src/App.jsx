import { HashRouter, Navigate, Route, Routes } from 'react-router-dom'
import AdminLayout from './components/AdminLayout.jsx'
import AdminOrders from './pages/AdminOrders.jsx'
import AdminShipping from './pages/AdminShipping.jsx'
import AdminProducts from './pages/AdminProducts.jsx'
import AdminRefunds from './pages/AdminRefunds.jsx'
import AdminMqLogs from './pages/AdminMqLogs.jsx'
import AdminResetRequests from './pages/AdminResetRequests.jsx'
import AdminResetPassword from './pages/AdminResetPassword.jsx'
import StockMaintenance from './pages/StockMaintenance.jsx'
import StockExcelEditor from './pages/StockExcelEditor.jsx'
import UserList from './pages/UserList.jsx'
import Download from './pages/Download.jsx'
import PaymentConfig from './pages/PaymentConfig.jsx'
import Reconciliation from './pages/Reconciliation.jsx'
import Login from './pages/Login.jsx'
import { RequireAdmin } from './components/RouteGuard.jsx'

const admin = (node) => <RequireAdmin>{node}</RequireAdmin>

// 管理后台独立工程：登录页 + 管理布局（左侧功能导航 + 右侧内容 + 详情抽屉）
export default function App() {
  return (
    <HashRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/admin" element={admin(<AdminLayout />)}>
          <Route index element={<Navigate to="/admin/orders" replace />} />
          <Route path="orders" element={<AdminOrders />} />
          <Route path="shipping" element={<AdminShipping />} />
          <Route path="products" element={<AdminProducts />} />
          <Route path="refunds" element={<AdminRefunds />} />
          <Route path="mq-logs" element={<AdminMqLogs />} />
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
    </HashRouter>
  )
}
