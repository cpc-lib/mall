import Vue from 'vue'
import VueRouter from 'vue-router'
import Login from '../views/Login'
import AdminLayout from '../components/AdminLayout'
import AdminOrders from '../views/AdminOrders'
import AdminShipping from '../views/AdminShipping'
import AdminProducts from '../views/AdminProducts'
import AdminRefunds from '../views/AdminRefunds'
import AdminMqLogs from '../views/AdminMqLogs'
import AdminResetRequests from '../views/AdminResetRequests'
import AdminResetPassword from '../views/AdminResetPassword'
import StockMaintenance from '../views/StockMaintenance'
import StockExcelEditor from '../views/StockExcelEditor'
import UserList from '../views/UserList'
import Download from '../views/Download'
import PaymentConfig from '../views/PaymentConfig'
import Reconciliation from '../views/Reconciliation'
import { getAccessToken, getUser } from '../utils/authStore'

Vue.use(VueRouter)
// 管理后台独立工程：登录页 + 管理布局（左侧功能导航 + 右侧内容 + 详情抽屉）
const router = new VueRouter({ routes: [
  { path: '/login', component: Login },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true, requiresAdmin: true },
    children: [
      { path: '', redirect: '/admin/orders' },
      { path: 'orders', component: AdminOrders },
      { path: 'shipping', component: AdminShipping },
      { path: 'products', component: AdminProducts },
      { path: 'refunds', component: AdminRefunds },
      { path: 'mq-logs', component: AdminMqLogs },
      { path: 'users', component: UserList },
      { path: 'reset-requests', component: AdminResetRequests },
      { path: 'reset-password', component: AdminResetPassword },
      { path: 'stock-maintenance', component: StockMaintenance },
      { path: 'download', component: Download },
      { path: 'payment-config', component: PaymentConfig },
      { path: 'reconciliation', component: Reconciliation }
    ]
  },
  // 全屏 Excel 编辑器（新页签打开，铺满整页）
  { path: '/admin/stock-edit/:id', component: StockExcelEditor, meta: { requiresAuth: true, requiresAdmin: true } },
  { path: '/', redirect: '/admin/orders' },
  { path: '*', redirect: '/admin/orders' }
] })
router.beforeEach((to, from, next) => {
  if (to.matched.some(r => r.meta.requiresAuth) && !getAccessToken()) return next({ path: '/login', query: { redirect: to.fullPath } })
  // 管理后台独立工程：非管理员一律回登录页（避免与 '/' 重定向形成循环）
  if (to.matched.some(r => r.meta.requiresAdmin) && (!getUser() || getUser().role !== 'ROLE_ADMIN')) return next('/login')
  next()
})
export default router
