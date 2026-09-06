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
      <div v-if="items.length === 0" class="tb-cardbox">
        <a-empty description="购物车还是空的，去挑点好物吧"/>
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
        <div class="m-pay-label">支付方式</div>
        <div class="m-pay-list">
          <div v-for="c in channels" :key="c.code" class="m-pay-item" :class="{active: c.code === channelCode}" @click="channelCode = c.code">
            <div class="m-pay-logo" :class="c.cls">{{c.glyph}}</div>
            <div class="m-pay-info">
              <div class="m-pay-name">{{c.name}}</div>
              <div class="m-pay-desc">{{c.desc}}</div>
            </div>
            <div class="m-pay-check"></div>
          </div>
        </div>
      </div>
      <div v-if="items.length > 0" class="tb-cart-bar">
        <span class="m-dim">已选 <b class="m-count">{{selectedItems.length}}</b> 件商品</span>
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
import { mallImg, onMallImgError } from '../assets/mallImgs'

var CHANNEL_META = {
  WXPAY: { name: '微信支付', desc: '使用微信扫码支付（沙箱演示）', glyph: '微', cls: 'wx' },
  ALIPAY: { name: '支付宝', desc: '跳转支付宝完成支付（沙箱演示）', glyph: '支', cls: 'ali' }
}
function channelMetaOf(code) {
  return CHANNEL_META[code] || { name: code, desc: '沙箱演示渠道', glyph: String(code || '?').slice(0, 1), cls: '' }
}

export default {
  data() { return { items: [], apps: [], channelCode: '', receiver: { name: '', phone: '', address: '' } } },
  computed: {
    // 渠道级去重：同渠道多应用时默认取第一个启用应用，用户视角只感知「支付方式」
    channels() {
      var seen = {}
      var list = []
      this.apps.forEach(a => {
        if (!a.channelCode || seen[a.channelCode]) return
        seen[a.channelCode] = true
        var meta = channelMetaOf(a.channelCode)
        list.push({ code: a.channelCode, appId: a.id, name: meta.name, desc: meta.desc, glyph: meta.glyph, cls: meta.cls })
      })
      return list
    },
    selectedItems() { return this.items.filter(i => i.selected) },
    total() { return this.selectedItems.reduce((s, i) => s + Number(i.latestPrice || 0) * Number(i.quantity || 0), 0) },
    canCheckout() { return this.selectedItems.length > 0 && this.selectedItems.every(i => i.available) && !!this.channelCode }
  },
  created() { this.load() },
  methods: {
    mallImg,
    onMallImgError,
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    async load() {
      const rs = await Promise.all([cartApi.list(), paymentConfigApi.listEnabledApps()])
      this.items = rs[0].data || []
      this.apps = rs[1].data || []
      if (!this.channels.some(c => c.code === this.channelCode)) {
        this.channelCode = this.channels.length ? this.channels[0].code : ''
      }
    },
    async select(row, v) { await cartApi.select(row.productId, v); await this.load() },
    async changeQty(row, v) { await cartApi.put({ productId: row.productId, quantity: Number(v || 1), selected: !!row.selected }); await this.load() },
    async remove(row) { await cartApi.remove(row.productId); await this.load() },
    async checkout() {
      const app = this.apps.find(a => a.channelCode === this.channelCode)
      if (!app) return this.$message.error('请选择支付方式')
      const paymentType = this.channelCode === 'WXPAY' ? '微信' : this.channelCode === 'ALIPAY' ? '支付宝' : ''
      if (!paymentType) return this.$message.error('暂不支持渠道：' + this.channelCode)
      const r = await checkoutApi.create({
        paymentType: paymentType, paymentAppId: app.id,
        receiverName: this.receiver.name || undefined,
        receiverPhone: this.receiver.phone || undefined,
        receiverAddress: this.receiver.address || undefined
      })
      this.$message.success('订单已创建：' + r.data.order.orderNo); this.$router.push('/orders')
    }
  }
}
</script>
