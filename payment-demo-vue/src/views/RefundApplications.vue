<template>
  <div class="tb-page"><div class="container">
    <h2 class="tb-h2">我的退款申请</h2>
    <p class="tb-page-tip">仅待审核（APPLYING）可编辑/撤销；受理后冻结额度并走审核/渠道退款链路。金额以服务端核算为准。</p>
    <div v-if="!list.length" class="tb-cardbox"><p class="m-list-empty">暂无退款申请</p></div>
    <div v-for="r in list" :key="r.apply.refundNo" class="m-list-card">
      <div class="m-list-head">
        <div class="m-list-no">退款号 {{r.apply.refundNo}}<br/>订单号 {{r.apply.orderNo}}</div>
        <div class="m-list-tags"><el-tag :type="r.apply.status==='FAILED'?'danger':r.apply.status==='SUCCESS'?'success':'info'" size="mini">{{statusLabel(r.apply.status)}}</el-tag></div>
      </div>
      <div class="m-list-sub">
        <span class="m-list-sub-label">{{typeLabel(r.apply.refundType)}}</span>
        <span class="m-list-amount">¥{{money(r.apply.refundAmount)}}</span>
      </div>
      <div class="m-list-items">
        <div v-for="i in r.items" :key="i.id">item#{{i.orderItemId}} <span class="m-list-item-sub">× {{i.refundQty}} @ ¥{{money(i.unitPrice)}}</span></div>
        <div>原因：{{r.apply.reason}}</div>
        <div v-if="r.apply.status==='FAILED'" class="m-list-fail">失败原因：{{r.failReason || '渠道退款失败，请联系管理员重试'}}</div>
      </div>
      <div class="m-list-actions">
        <template v-if="r.apply.status==='APPLYING'">
          <el-button size="mini" @click="beginEdit(r)">编辑</el-button>
          <el-button size="mini" type="danger" @click="cancel(r)">撤销</el-button>
        </template>
        <span v-else class="m-list-item-sub">已受理，不可编辑/撤销</span>
      </div>
    </div>
    <el-dialog title="编辑待审核退款申请" :visible.sync="dialog" width="560px">
      <div v-for="i in editItems" :key="i.id" class="m-modal-line"><b>item#{{i.orderItemId}}</b><el-input-number v-model="quantities[i.orderItemId]" :min="0" :max="maxFor(i)" /><span class="m-modal-hint">最大 {{maxFor(i)}} 件</span></div>
      <el-input v-model.trim="reason" maxlength="255" placeholder="退款原因"/><div style="margin-top:12px">修改后预估金额：¥{{money(editTotal)}}</div>
      <span slot="footer"><el-button @click="dialog=false">取消</el-button><el-button type="primary" :disabled="editTotal<=0" @click="save">保存</el-button></span>
    </el-dialog>
  </div></div>
</template>
<script>
import refundApi from '../api/refundApply'
import checkoutApi from '../api/checkout'
import { availableRefundQuantityExcluding, refundAmountEstimate } from '../utils/refundQuota'
import { REFUND_STATUS_LABEL, REFUND_TYPE_LABEL } from '../utils/statusLabels'
export default {
  data() { return { list: [], orders: [], editing: null, dialog: false, reason: '', quantities: {} } },
  computed: {
    editItems() { return this.editing ? this.editing.items || [] : [] },
    editTotal() { return this.editItems.reduce((s, i) => { const oi = this.findOrderItem(i.orderItemId); return s + (oi ? refundAmountEstimate(oi, this.quantities[i.orderItemId] || 0) : 0) }, 0) }
  },
  created() { this.load() },
  methods: {
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    typeLabel(v) { return REFUND_TYPE_LABEL[v] || v },
    statusLabel(v) { return REFUND_STATUS_LABEL[v] || v },
    async load() { const rs = await Promise.all([refundApi.mine(), checkoutApi.list()]); this.list = rs[0].data || []; this.orders = rs[1].data || [] },
    findOrderItem(id) { for (const o of this.orders) { const f = (o.items || []).find(i => Number(i.id) === Number(id)); if (f) return f } return null },
    maxFor(item) { const oi = this.findOrderItem(item.orderItemId); return oi ? availableRefundQuantityExcluding(oi, item.refundQty) : Number(item.refundQty || 0) },
    beginEdit(row) { this.editing = row; this.reason = row.apply.reason || ''; const q = {}; (row.items || []).forEach(i => { q[i.orderItemId] = i.refundQty }); this.quantities = q; this.dialog = true },
    async save() { const items = this.editItems.map(i => ({ orderItemId: i.orderItemId, quantity: Number(this.quantities[i.orderItemId] || 0) })).filter(i => i.quantity > 0); if (!items.length) return this.$message.error('至少保留一个退款商品'); await refundApi.update(this.editing.apply.refundNo, { reason: this.reason, items }); this.$message.success('退款申请已修改'); this.dialog = false; await this.load() },
    async cancel(row) { await this.$confirm('确认撤销该待审核退款申请？冻结额度将释放。', '提示'); await refundApi.cancel(row.apply.refundNo); this.$message.success('已撤销'); await this.load() }
  }
}
</script>
