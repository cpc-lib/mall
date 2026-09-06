<template>
  <div style="min-height:100vh;background:#001529;display:flex;align-items:center;justify-content:center">
    <el-card style="width:380px">
      <div slot="header" style="text-align:center;font-size:16px;font-weight:600">支付演示 · 管理后台</div>
      <el-form :model="loginForm" label-position="top" @submit.native.prevent="login">
        <el-form-item label="用户名"><el-input v-model.trim="loginForm.username" placeholder="管理员账号" autocomplete="username"/></el-form-item>
        <el-form-item label="密码"><el-input v-model="loginForm.password" type="password" show-password placeholder="密码（至少8位）" autocomplete="current-password" @keyup.enter.native="login"/></el-form-item>
        <el-button type="primary" :loading="loading" style="width:100%" @click="login">登 录</el-button>
      </el-form>
    </el-card>
  </div>
</template>
<script>
import authApi from '../api/auth'
import { clearAuth, saveAuth } from '../utils/authStore'
export default {
  data() { return { loading: false, loginForm: { username: '', password: '' } } },
  methods: {
    async login() {
      if (!this.loginForm.username) return this.$message.error('请输入用户名')
      if (!this.loginForm.password || this.loginForm.password.length < 8) return this.$message.error('密码至少8位')
      this.loading = true
      try {
        const r = await authApi.login(this.loginForm)
        const u = r.data && r.data.user
        if (!u || u.role !== 'ROLE_ADMIN') {
          saveAuth(r.data); clearAuth()
          return this.$message.error('仅管理员可登录本系统')
        }
        saveAuth(r.data)
        this.$message.success('登录成功')
        this.$router.replace(this.$route.query.redirect || '/admin/orders')
      } finally { this.loading = false }
    }
  }
}
</script>
