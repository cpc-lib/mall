<template>
  <!-- 手机 App 底部 Tab 栏：首页 / 购物车 / 订单 / 我的 -->
  <nav class="m-tabbar">
    <router-link v-for="t in tabs" :key="t.to" :to="t.to"
      :class="['m-tab', { active: isActive(t.to) }]">
      <span class="ico">{{ t.ico }}</span>{{ t.label }}
    </router-link>
  </nav>
</template>
<script>
export default {
  data() {
    return {
      tabs: [
        { to: '/', ico: '🏠', label: '首页' },
        { to: '/cart', ico: '🛒', label: '购物车' },
        { to: '/orders', ico: '📦', label: '订单' },
        { to: '/account', ico: '👤', label: '我的' }
      ]
    }
  },
  methods: {
    isActive(to) { return to === '/' ? this.$route.path === '/' : this.$route.path === to }
  }
}
</script>
