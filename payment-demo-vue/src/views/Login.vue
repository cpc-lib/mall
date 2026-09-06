<template>
  <div class="tb-auth">
    <div class="tb-auth-hero">
      <div class="tb-auth-deco tb-auth-deco-a"></div>
      <div class="tb-auth-deco tb-auth-deco-b"></div>
      <div class="tb-auth-brand">
        <div class="tb-auth-logo">淘</div>
        <div class="tb-auth-name">淘支付<small>演示商城</small></div>
      </div>
      <p class="tb-auth-tagline">安全 · 便捷 · 极速的支付演示体验</p>
    </div>

    <div class="tb-auth-card">
      <div class="tb-auth-switch">
        <button type="button" :class="{ on: tab === 'login' }" @click="tab = 'login'">登 录</button>
        <button type="button" :class="{ on: tab === 'register' }" @click="tab = 'register'">免费注册</button>
      </div>

      <el-form v-if="tab === 'login'" :model="loginForm" label-position="top" class="tb-auth-form" @submit.native.prevent="login">
        <el-form-item label="用户名">
          <el-input v-model.trim="loginForm.username" autocomplete="username" placeholder="请输入用户名"><i slot="prefix" class="tb-auth-ico">👤</i></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="loginForm.password" type="password" show-password autocomplete="current-password" placeholder="请输入密码（至少 8 位）" @keyup.enter.native="login"><i slot="prefix" class="tb-auth-ico">🔒</i></el-input>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="tb-auth-submit" @click="login">登 录</el-button>
        <div class="tb-auth-forgot"><el-button type="text" @click="fpVisible=true">忘记密码？</el-button></div>
      </el-form>

      <el-form v-else :model="registerForm" label-position="top" class="tb-auth-form" @submit.native.prevent="register">
        <el-form-item label="用户名">
          <el-input v-model.trim="registerForm.username" autocomplete="username" placeholder="请输入用户名"><i slot="prefix" class="tb-auth-ico">👤</i></el-input>
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="registerForm.password" type="password" show-password autocomplete="new-password" placeholder="请输入密码（至少 8 位）" @keyup.enter.native="register"><i slot="prefix" class="tb-auth-ico">🔒</i></el-input>
        </el-form-item>
        <el-button type="primary" :loading="loading" class="tb-auth-submit" @click="register">注 册</el-button>
        <div class="tb-auth-tip">注册即代表同意演示环境使用规范，账号数据仅用于本地演示</div>
      </el-form>
    </div>

    <p class="tb-auth-foot">淘支付 · 支付能力演示 Demo</p>

    <el-dialog title="找回密码" :visible.sync="fpVisible" width="420px">
      <p style="color:#999;font-size:12px;margin-top:0">提交后请联系管理员处理，管理员将为你重置新密码。</p>
      <el-input v-model.trim="fpForm.username" placeholder="用户名（必填）" style="margin-bottom:12px"/>
      <el-input v-model.trim="fpForm.remark" maxlength="255" type="textarea" :rows="3" placeholder="补充说明（选填，如注册手机/邮箱等）"/>
      <span slot="footer"><el-button @click="fpVisible=false">取消</el-button><el-button type="primary" @click="submitForgot">提交申请</el-button></span>
    </el-dialog>
  </div>
</template>
<script>
import authApi from '../api/auth'
import { saveAuth } from '../utils/authStore'
export default {
  data() { return { tab: 'login', loading: false, loginForm: { username: '', password: '' }, registerForm: { username: '', password: '' }, fpVisible: false, fpForm: { username: '', remark: '' } } },
  methods: {
    valid(f) { if (!f.username) { this.$message.error('请输入用户名'); return false } if (!f.password || f.password.length < 8) { this.$message.error('密码至少8位'); return false } return true },
    async login() {
      if (!this.valid(this.loginForm)) return
      this.loading = true
      try {
        const r = await authApi.login(this.loginForm)
        saveAuth(r.data)
        this.$message.success('登录成功')
        // 用户商城工程无管理页面：管理员登录后同样留在商城首页
        const redirect = this.$route.query.redirect
        this.$router.replace(redirect && redirect.indexOf('/admin') !== 0 ? redirect : '/')
      } finally { this.loading = false }
    },
    async register() { if (!this.valid(this.registerForm)) return; this.loading = true; try { await authApi.register(this.registerForm); this.$message.success('注册成功，请登录'); this.loginForm.username = this.registerForm.username; this.tab = 'login' } finally { this.loading = false } },
    async submitForgot() {
      if (!this.fpForm.username) return this.$message.error('请输入用户名')
      try {
        await authApi.submitPasswordResetRequest({ username: this.fpForm.username, remark: (this.fpForm.remark || '').trim() })
        this.$message.success('申请已提交，请联系管理员获取新密码')
        this.fpVisible = false; this.fpForm = { username: '', remark: '' }
      } catch (e) { /* 错误提示由请求拦截器统一弹出 */ }
    }
  }
}
</script>
