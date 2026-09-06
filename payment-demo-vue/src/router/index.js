import Vue from 'vue'
import VueRouter from 'vue-router'
import Index from '../views/index'
import Login from '../views/Login'
import Cart from '../views/Cart'
import Orders from '../views/Orders'
import RefundApplications from '../views/RefundApplications'
import Account from '../views/Account'
import Success from '../views/Success'
import { getAccessToken } from '../utils/authStore'

Vue.use(VueRouter)
// 用户商城（淘宝风格）：管理后台已拆分至独立工程 payment-demo-vue-admin
const router = new VueRouter({ routes: [
  { path: '/', component: Index },
  { path: '/login', component: Login },
  { path: '/success', component: Success },
  { path: '/cart', component: Cart, meta: { requiresAuth: true } },
  { path: '/orders', component: Orders, meta: { requiresAuth: true } },
  { path: '/orders-v2', redirect: '/orders' },
  { path: '/refund-applications', component: RefundApplications, meta: { requiresAuth: true } },
  { path: '/account', component: Account, meta: { requiresAuth: true } },
  { path: '*', redirect: '/' }
] })
router.beforeEach((to, from, next) => {
  if (to.matched.some(r => r.meta.requiresAuth) && !getAccessToken()) return next({ path: '/login', query: { redirect: to.fullPath } })
  next()
})
export default router
