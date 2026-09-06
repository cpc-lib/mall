<template>
  <el-container style="height:100vh">
    <el-header style="background:#001529;display:flex;align-items:center;justify-content:space-between;padding:0 24px;box-shadow:0 2px 8px rgba(0,0,0,.15);z-index:1">
      <span style="color:#fff;font-size:16px;font-weight:600;letter-spacing:1px">支付业务演示 · 管理后台</span>
      <div>
        <el-button type="text" style="color:#fff" @click="$router.push('/')">前台首页</el-button>
        <span style="color:rgba(255,255,255,.65);margin:0 12px">{{ username }}</span>
        <el-button type="text" style="color:#fff" @click="logout">退出登录</el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside width="200px" style="background:#fff;border-right:1px solid #EBEEF5">
        <el-menu :default-active="active" router style="border-right:0;padding-top:8px">
          <el-menu-item v-for="m in menus" :key="m.path" :index="m.path">{{ m.label }}</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main style="background:#F5F6F8;padding:24px;overflow-x:auto">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script>
import authApi from '../api/auth'
import { clearAuth, getRefreshToken, getUser } from '../utils/authStore'

// 管理后台独立布局：顶栏 + 左侧功能导航 + 右侧内容（列表/表单），与商城前台 UI 完全隔离
export default {
  name: 'AdminLayout',
  data() {
    return {
      menus: [
        { path: '/admin/orders', label: '订单管理' },
        { path: '/admin/shipping', label: '订单发货' },
        { path: '/admin/products', label: '商品库存' },
        { path: '/admin/refunds', label: '退款受理' },
        { path: '/admin/mq-logs', label: 'MQ/库存异常' },
        { path: '/admin/users', label: '用户列表' },
        { path: '/admin/reset-requests', label: '密码重置申请' },
        { path: '/admin/reset-password', label: '重置用户密码' },
        { path: '/admin/stock-maintenance', label: '批量库存维护' },
        { path: '/admin/download', label: '下载账单' },
        { path: '/admin/payment-config', label: '支付配置' },
        { path: '/admin/reconciliation', label: '对账管理' }
      ]
    }
  },
  computed: {
    username() { const u = getUser(); return (u && u.username) || '-' },
    active() { const hit = this.menus.find(m => this.$route.path.startsWith(m.path)); return hit ? hit.path : '/admin/orders' }
  },
  methods: {
    async logout() {
      try { await authApi.logout(getRefreshToken()) } finally { clearAuth(); this.$router.replace('/login') }
    }
  }
}
</script>
