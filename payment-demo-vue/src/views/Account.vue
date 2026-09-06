<template>
  <div class="tb-page"><div class="container">
    <h2 class="tb-h2">我的</h2>
    <div class="tb-cardbox m-quick">
      <router-link class="m-quick-item" to="/orders">📦 我的订单<span class="arrow">›</span></router-link>
      <router-link class="m-quick-item" to="/refund-applications">💸 我的退款申请<span class="arrow">›</span></router-link>
      <router-link class="m-quick-item" to="/cart">🛒 购物车<span class="arrow">›</span></router-link>
    </div>
    <div class="tb-cardbox" style="max-width:520px">
      <div class="tb-account-head">
        <div class="tb-account-avatar">{{(user && user.username ? user.username : 'U').slice(0, 1).toUpperCase()}}</div>
        <div>
          <div class="tb-account-name">{{user ? user.username : '-'}}</div>
          <div class="tb-account-role">{{user ? user.role : '-'}}</div>
        </div>
      </div>
      <el-form label-position="top">
        <el-form-item label="原密码"><el-input v-model="form.oldPassword" type="password" show-password /></el-form-item>
        <el-form-item label="新密码（至少8位）"><el-input v-model="form.newPassword" type="password" show-password /></el-form-item>
        <el-button type="primary" @click="changePassword">修改密码</el-button>
        <el-button @click="logout">退出登录</el-button>
      </el-form>
    </div>
    <p class="m-demo-note">淘支付商城 · 支付业务演示（支付宝 / 微信支付沙箱）<br/>仅供学习演示，请勿用于真实交易</p>
  </div></div>
</template>
<script>
import authApi from '../api/auth'
import { clearAuth, getRefreshToken, getUser } from '../utils/authStore'
export default {
  data() { return { user: getUser(), form: { oldPassword: '', newPassword: '' } } },
  methods: {
    async changePassword() { if (!this.form.oldPassword || !this.form.newPassword || this.form.newPassword.length < 8) return this.$message.error('请正确填写密码，新密码至少8位'); await authApi.changePassword(this.form); clearAuth(); this.$message.success('密码修改成功，请重新登录'); this.$router.replace('/login') },
    async logout() { try { await authApi.logout(getRefreshToken()) } finally { clearAuth(); this.$router.replace('/login') } }
  }
}
</script>
