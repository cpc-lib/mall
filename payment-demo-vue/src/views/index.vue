<template>
  <div class="tb-page">
    <div class="container">
      <div class="tb-section-title">全部商品<span>支付演示商城 · 支付宝 / 微信支付沙箱演示</span></div>
      <div v-loading="loading" style="min-height:200px">
        <div v-if="!loading && products.length===0" class="tb-cardbox"><el-empty description="暂无商品"/></div>
        <div class="tb-grid" v-else>
          <div class="tb-card" v-for="p in products" :key="p.id">
            <div class="tb-card-img"><img :src="mallImg(p.id)" :alt="p.title" loading="lazy"/></div>
            <div class="tb-card-body">
              <div class="tb-card-title">{{p.title}}</div>
              <div class="tb-card-price"><span class="tb-rmb">¥</span>{{money(p.price)}}<span class="tb-card-stock">{{soldOut(p) ? '缺货/下架' : '库存 ' + p.stock}}</span></div>
              <button v-if="!isAdmin" class="tb-card-btn" :disabled="soldOut(p)" @click="add(p)">加入购物车</button>
              <div v-else class="tb-card-admin">管理员账号不支持下单，请前往管理后台维护商品</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
<script>
import productApi from '../api/product'
import cartApi from '../api/cart'
import { getAccessToken, getUser } from '../utils/authStore'
import { mallImg, onMallImgError } from '../assets/mallImgs'
export default {
  data() { return { loading: true, products: [] } },
  computed: { isAdmin() { const u = getUser(); return !!u && u.role === 'ROLE_ADMIN' } },
  created() { productApi.list().then(r => { this.products = r.data.productList || [] }).finally(() => { this.loading = false }) },
  methods: {
    mallImg,
    onMallImgError,
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    soldOut(p) { return p.productStatus !== 'ENABLED' || Number(p.stock || 0) <= 0 },
    async add(p) { if (!getAccessToken()) return this.$router.push('/login'); await cartApi.put({ productId: p.id, quantity: 1, selected: true }); this.$message.success('已加入购物车') }
  }
}
</script>
