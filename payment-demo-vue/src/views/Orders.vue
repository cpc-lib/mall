<template>
  <div class="tb-page"><div class="container">
    <h2 class="tb-h2">我的订单</h2>
    <p class="tb-page-tip">退款金额由服务端按订单快照核算（最后一件吃尾差），页面金额仅为预估。</p>
    <div class="tb-cardbox tb-table-card">
    <el-table :data="orders" style="width:100%">
      <el-table-column label="订单号" min-width="190"><template slot-scope="s">{{s.row.order.orderNo}}</template></el-table-column>
      <el-table-column label="支付方式" width="100"><template slot-scope="s">{{s.row.order.paymentType}}</template></el-table-column>
      <el-table-column label="金额" width="110"><template slot-scope="s">¥{{money(s.row.order.totalFee)}}</template></el-table-column>
      <el-table-column label="状态" min-width="150"><template slot-scope="s"><el-tag v-for="t in statusTags(s.row.order)" :key="t" size="small" style="margin-right:4px">{{t}}</el-tag></template></el-table-column>
      <el-table-column label="商品明细（快照价）" min-width="300"><template slot-scope="s"><div v-for="i in s.row.items" :key="i.id">{{i.productTitle}} × {{i.quantity}} @ ¥{{money(i.unitPrice)}}；已退 {{i.refundedQty||0}}<span v-if="(i.refundFrozenQty||0)>0">，冻结 {{i.refundFrozenQty}}</span></div></template></el-table-column>
      <el-table-column label="操作" width="300">
        <template slot-scope="s">
          <el-button v-if="canPay(s.row)" size="mini" type="primary" @click="pay(s.row)">支付</el-button>
          <el-button v-if="canCancel(s.row)" size="mini" @click="cancelOrder(s.row)">取消订单</el-button>
          <el-button v-if="canLogistics(s.row)" size="mini" @click="showLogistics(s.row)">物流详情</el-button>
          <el-button v-if="canConfirm(s.row)" size="mini" type="primary" plain @click="confirmReceipt(s.row)">确认收货</el-button>
          <el-button v-if="canRefund(s.row)" size="mini" @click="beginRefund(s.row)">分项退款</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>
    <el-dialog title="分项退款" :visible.sync="dialog" width="560px">
      <div v-if="target" style="margin-bottom:12px">
        <el-radio-group v-model="refundType">
          <el-radio v-if="target.order.fulfillmentStatus==='WAIT_SHIP'" label="CANCEL_BEFORE_SHIP">未发货取消（补库存）</el-radio>
          <el-radio v-if="target.order.fulfillmentStatus==='SHIPPED'" label="REFUND_ONLY">仅退款（未收货）</el-radio>
          <el-radio v-if="target.order.fulfillmentStatus==='RECEIVED'" label="REFUND_ONLY">仅退款</el-radio>
          <el-radio v-if="target.order.fulfillmentStatus==='RECEIVED'" label="RETURN_AND_REFUND">退货退款（签收质检后补库存）</el-radio>
        </el-radio-group>
      </div>
      <div v-for="i in targetItems" :key="i.id" style="margin-bottom:12px"><b>{{i.productTitle}}</b>：<el-input-number v-model="qty[i.id]" :min="0" :max="available(i)" :disabled="available(i)<=0" /> / 可退 {{available(i)}} 件，快照价 ¥{{money(i.unitPrice)}}</div>
      <el-input v-model.trim="reason" maxlength="255" placeholder="退款原因" />
      <div style="margin-top:12px">预估退款：<b>¥{{money(estimateTotal)}}</b>（以服务端核算为准）</div>
      <span slot="footer"><el-button @click="dialog=false">取消</el-button><el-button type="primary" :disabled="estimateTotal<=0" @click="submitRefund">提交申请</el-button></span>
    </el-dialog>
    <el-dialog title="物流详情" :visible.sync="logisticsDialog" width="480px">
      <div v-if="logistics && logistics.shipped">
        <p>快递公司：{{logistics.logisticsCompany}}</p>
        <p>运单号：{{logistics.trackingNo}}</p>
        <p>物流状态：{{shipmentLabel(logistics.status)}}</p>
        <p v-for="(v, k) in logisticsTimeline" :key="k">{{shipmentLabel(k)}}：{{v}}</p>
      </div>
      <p v-else>订单尚未发货</p>
    </el-dialog>
    <el-dialog title="微信扫码支付" :visible.sync="wxDialog" width="380px" center>
      <div style="text-align:center">
        <div v-if="codeUrl" class="qr-frame"><qriously :value="codeUrl" :size="280"/></div>
        <p style="margin:14px 0 4px;font-weight:600">请使用微信扫描二维码完成支付</p>
        <p style="color:#999;font-size:12px;margin:0">支付成功后页面自动刷新，无需手动关闭</p>
        <div style="margin-top:8px;word-break:break-all;color:#bbb;font-size:11px">{{codeUrl}}</div>
      </div>
    </el-dialog>
  </div></div>
</template>
<script>
import checkoutApi from '../api/checkout'
import refundApi from '../api/refundApply'
import shipmentApi from '../api/shipment'
import { availableRefundQuantity, refundAmountEstimate } from '../utils/refundQuota'
import { SHIPMENT_LABEL, statusTags } from '../utils/statusLabels'
export default {
  data() { return { orders: [], dialog: false, target: null, refundType: 'REFUND_ONLY', qty: {}, reason: '用户申请退款', wxDialog: false, codeUrl: '', shipments: {}, logistics: null, logisticsDialog: false } },
  computed: {
    targetItems() { return this.target ? this.target.items || [] : [] },
    estimateTotal() { return this.targetItems.reduce((s, i) => s + refundAmountEstimate(i, this.qty[i.id] || 0), 0) },
    logisticsTimeline() {
      const t = (this.logistics && this.logistics.timeline) || {}
      const out = {}
      Object.keys(t).forEach(k => { if (t[k]) out[k] = new Date(t[k]).toLocaleString() })
      return out
    }
  },
  created() { this.load() },
  methods: {
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    statusTags(order) { return statusTags(order) },
    shipmentLabel(v) { return SHIPMENT_LABEL[v] || v },
    async load() {
      const r = await checkoutApi.list(); this.orders = r.data || []
      // 已发货订单拉取物流状态，用于「确认收货」按钮可用性
      const shipped = this.orders.filter(d => d.order.fulfillmentStatus === 'SHIPPED')
      const entries = await Promise.all(shipped.map(async d => {
        try { const s = await shipmentApi.getShipment(d.order.orderNo); return [d.order.orderNo, (s.data && s.data.status) || ''] } catch (e) { return [d.order.orderNo, ''] }
      }))
      const map = {}
      entries.forEach(([k, v]) => { map[k] = v })
      this.shipments = map
    },
    canPay(d) { const o = d.order; return o.payStatus === 'UNPAID' && o.fulfillmentStatus !== 'CANCELLED' && (o.orderStatus === '未支付' || o.orderStatus === 'WAIT_PAY') },
    canCancel(d) { const o = d.order; return o.payStatus === 'PAID' && o.fulfillmentStatus === 'WAIT_SHIP' },
    canLogistics(d) { return ['SHIPPED', 'RECEIVED'].indexOf(d.order.fulfillmentStatus) >= 0 },
    canConfirm(d) { return d.order.fulfillmentStatus === 'SHIPPED' && this.shipments[d.order.orderNo] === 'DELIVERED' },
    canRefund(d) { return d.order.payStatus === 'PAID' && (d.items || []).some(i => availableRefundQuantity(i) > 0) },
    available(i) { return availableRefundQuantity(i) },
    async pay(d) {
      if (d.order.paymentType === '支付宝') { const r = await checkoutApi.alipay(d.order.orderNo); const w = window.open('', '_blank'); if (w) { w.document.open(); w.document.write((r.data && r.data.html) || ''); w.document.close() } } else { const r = await checkoutApi.wxpay(d.order.orderNo); this.codeUrl = (r.data && (r.data.codeUrl || r.data.code_url)) || ''; this.wxDialog = true }
    },
    cancelOrder(d) {
      this.$confirm('已付款未发货订单将创建「未发货取消」退款申请，受理后自动原路退回，库存自动回补。', '取消订单').then(async () => {
        const r = await shipmentApi.cancelPaidOrder(d.order.orderNo)
        const amount = r.data && r.data.apply ? r.data.apply.refundAmount : 0
        this.$message.success('取消申请已提交，预估退款 ¥' + this.money(amount)); await this.load()
      }).catch(() => {})
    },
    async showLogistics(d) { const r = await shipmentApi.getShipment(d.order.orderNo); this.logistics = r.data || { shipped: false }; this.logisticsDialog = true },
    async confirmReceipt(d) { await shipmentApi.confirmReceipt(d.order.orderNo); this.$message.success('确认收货成功，交易完成'); await this.load() },
    beginRefund(d) {
      this.target = d; const q = {}; (d.items || []).forEach(i => { q[i.id] = 0 }); this.qty = q; this.reason = '用户申请退款'
      const f = d.order.fulfillmentStatus
      this.refundType = f === 'RECEIVED' || f === 'SHIPPED' ? 'REFUND_ONLY' : 'CANCEL_BEFORE_SHIP'
      this.dialog = true
    },
    async submitRefund() {
      const items = this.targetItems.filter(i => Number(this.qty[i.id] || 0) > 0).map(i => ({ orderItemId: i.id, quantity: Number(this.qty[i.id]) }))
      if (!items.length) return this.$message.error('至少选择一个退款商品')
      const r = await refundApi.create({ orderNo: this.target.order.orderNo, refundType: this.refundType, reason: this.reason, items })
      const amount = r.data && r.data.apply ? r.data.apply.refundAmount : 0
      this.$message.success('退款申请已提交，服务端核算金额 ¥' + this.money(amount))
      this.dialog = false; await this.load()
    }
  }
}
</script>
