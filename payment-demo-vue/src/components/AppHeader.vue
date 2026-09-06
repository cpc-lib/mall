<template>
  <header class="m-bar">
    <div class="m-bar-row">
      <router-link to="/" class="m-logo">
        <span>🛍️</span>
        <span class="m-logo-text">淘支付<small>演示商城</small></span>
      </router-link>
      <router-link v-if="loggedIn" to="/account" class="m-user">👋 {{ username }}</router-link>
      <router-link v-else to="/login" class="m-user">登录 / 注册</router-link>
    </div>
    <div class="m-search">
      <input v-model="kw" placeholder="搜索好物（演示入口，回车逛全店）" @keyup.enter="go('/')"/>
      <button @click="go('/')">搜索</button>
    </div>
  </header>
</template>
<script>
import { getAccessToken, getUser } from '../utils/authStore'
export default {
  data() { return { authTick: 0, kw: '' } },
  computed: {
    loggedIn() { this.authTick; return !!getAccessToken() },
    username() { this.authTick; const u = getUser(); return (u && u.username) || '我的' }
  },
  methods: {
    go(path) { this.$router.push(path).catch(() => {}) }
  },
  created() { this.listener = () => { this.authTick += 1 }; window.addEventListener('payment-auth-changed', this.listener) },
  beforeDestroy() { window.removeEventListener('payment-auth-changed', this.listener) }
}
</script>
