<template>
  <div class="tb-page">
    <div class="container">
      <h2 class="tb-h2">我的购物车</h2>
      <div class="tb-cart-head">
        <div class="tb-col-check">勾选</div>
        <div class="tb-col-goods">商品信息</div>
        <div class="tb-col-price">单价</div>
        <div class="tb-col-qty">数量</div>
        <div class="tb-col-sum">小计</div>
        <div class="tb-col-op">操作</div>
      </div>
      <div v-if="items.length === 0" class="tb-cardbox" style="border-radius:0 0 8px 8px;text-align:center;padding:48px 24px">
        <div style="font-size:40px;line-height:1">🛒</div>
        <p style="margin:16px 0 0;font-size:14px;color:#9C9C9C">购物车还是空的，去挑点好物吧</p>
      </div>
      <div class="tb-cart-row" v-for="r in items" :key="r.productId">
        <div class="tb-col-check"><el-checkbox :value="!!r.selected" @change="select(r, $event)"/></div>
        <div class="tb-col-goods">
          <img :src="mallImg(r.productId)" :alt="r.title" @error="onMallImgError"/>
          <div style="min-width:0">
            <div class="tb-goods-title">{{r.title}}</div>
            <div class="tb-goods-sub">{{r.available ? '可售 · 可用库存 ' + r.availableStock : '不可售 / 库存不足'}}</div>
          </div>
        </div>
        <div class="tb-col-price">¥{{money(r.latestPrice)}}</div>
        <div class="tb-col-qty"><el-input-number :value="r.quantity" :min="1" :max="Math.max(1,Number(r.availableStock||1))" size="small" @change="changeQty(r, $event)"/></div>
        <div class="tb-col-sum">¥{{money(Number(r.latestPrice||0) * Number(r.quantity||0))}}</div>
        <div class="tb-col-op"><a @click="remove(r)">删除</a></div>
      </div>
      <div v-if="items.length > 0" class="tb-receiver">
        <el-input v-model.trim="receiver.name" placeholder="收货人姓名（选填）" style="width:140px"/>
        <el-input v-model.trim="receiver.phone" placeholder="收货人电话（选填）" style="width:160px"/>
        <el-input v-model.trim="receiver.address" placeholder="收货地址（选填，缺省模拟值）" style="width:320px"/>
        <span style="font-size:13px;color:#6C6C6C">支付应用：</span>
        <el-select v-model="paymentAppId" placeholder="选择支付应用" style="min-width:240px"><el-option v-for="a in apps" :key="a.id" :label="`${a.appName} / ${a.channelName||a.channelCode}`" :value="a.id"/></el-select>
      </div>
      <div v-if="items.length > 0" class="tb-cart-bar">
        <span style="font-size:13px;color:#6C6C6C">已选 <b style="color:#FF5000">{{selectedItems.length}}</b> 件商品</span>
        <div class="tb-cart-total">合计：<b>¥{{money(total)}}</b></div>
        <button class="tb-cart-checkout" :disabled="!canCheckout" @click="checkout">结 算</button>
      </div>
    </div>
  </div>
</template>
<script>
import cartApi from '../api/cart'
import checkoutApi from '../api/checkout'
import paymentConfigApi from '../api/paymentConfig'
import { mallImg } from '../assets/mallImgs'
export default {
  data() { return { items: [], apps: [], paymentAppId: null, receiver: { name: '', phone: '', address: '' } } },
  computed: {
    selectedApp() { return this.apps.find(a => Number(a.id) === Number(this.paymentAppId)) },
    selectedItems() { return this.items.filter(i => i.selected) },
    total() { return this.selectedItems.reduce((s, i) => s + Number(i.latestPrice || 0) * Number(i.quantity || 0), 0) },
    canCheckout() { return this.selectedItems.length > 0 && this.selectedItems.every(i => i.available) && !!this.selectedApp }
  },
  created() { this.load() },
  methods: {
    mallImg,
    onMallImgError,
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    async load() { const rs = await Promise.all([cartApi.list(), paymentConfigApi.listEnabledApps()]); this.items = rs[0].data || []; this.apps = rs[1].data || []; if (!this.apps.some(a => Number(a.id) === Number(this.paymentAppId))) this.paymentAppId = this.apps.length ? this.apps[0].id : null },
    async select(row, v) { await cartApi.select(row.productId, v); await this.load() },
    async changeQty(row, v) { await cartApi.put({ productId: row.productId, quantity: Number(v || 1), selected: !!row.selected }); await this.load() },
    async remove(row) { await cartApi.remove(row.productId); await this.load() },
    async checkout() {
      if (!this.selectedApp) return this.$message.error('请选择支付应用')
      const type = this.selectedApp.channelCode === 'WXPAY' ? '微信' : this.selectedApp.channelCode === 'ALIPAY' ? '支付宝' : ''
      if (!type) return this.$message.error('暂不支持渠道：' + this.selectedApp.channelCode)
      const r = await checkoutApi.create({
        paymentType: type, paymentAppId: this.selectedApp.id,
        receiverName: this.receiver.name || undefined,
        receiverPhone: this.receiver.phone || undefined,
        receiverAddress: this.receiver.address || undefined
      })
      this.$message.success('订单已创建：' + r.data.order.orderNo); this.$router.push('/orders')
    }
  }
}
</script>
