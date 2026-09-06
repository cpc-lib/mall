<template>
  <div class="tb-page">
    <div class="container">
      <div class="tb-success">
        <div class="tb-success-icon">
          <svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
            <circle cx="12" cy="12" r="10" />
            <polyline points="16 10 10 16 8 14" />
          </svg>
        </div>
        <h2>支付成功！</h2>
        <p class="tb-success-tip">感谢您的购买，课程已开通</p>
        <div class="tb-success-actions">
          <el-button type="primary" @click="goToOrders">查看订单</el-button>
          <el-button @click="goHome">返回首页</el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
export default {
  methods: {
    goToOrders() {
      this.$router.push('/orders')
    },
    goHome() {
      this.$router.push('/')
    }
  }
}
</script>
