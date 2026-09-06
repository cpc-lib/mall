<template>
  <div class="tb-page"><div class="container">
    <h2 class="tb-h2">我的退款申请</h2>
    <p class="tb-page-tip">仅待审核（APPLYING）可编辑/撤销；受理后冻结额度并走审核/渠道退款链路。金额以服务端核算为准。</p>
    <div class="tb-cardbox tb-table-card">
    <el-table :data="list" style="width:100%">
      <el-table-column label="退款号" min-width="190"><template slot-scope="s">{{s.row.apply.refundNo}}</template></el-table-column>
      <el-table-column label="订单号" min-width="190"><template slot-scope="s">{{s.row.apply.orderNo}}</template></el-table-column>
      <el-table-column label="类型" width="180"><template slot-scope="s">{{typeLabel(s.row.apply.refundType)}}</template></el-table-column>
      <el-table-column label="金额" width="100"><template slot-scope="s">¥{{money(s.row.apply.refundAmount)}}</template></el-table-column>
      <el-table-column label="状态" width="110"><template slot-scope="s"><el-tag :type="s.row.apply.status==='FAILED'?'danger':s.row.apply.status==='SUCCESS'?'success':'info'" size="mini">{{statusLabel(s.row.apply.status)}}</el-tag></template></el-table-column>
      <el-table-column label="失败原因" min-width="200"><template slot-scope="s"><span v-if="s.row.apply.status==='FAILED'" style="color:#f50;font-size:12px">{{s.row.failReason || '渠道退款失败，请联系管理员重试'}}</span><span v-else>-</span></template></el-table-column>
      <el-table-column label="原因"><template slot-scope="s">{{s.row.apply.reason}}</template></el-table-column>
      <el-table-column label="商品明细（快照价）" min-width="240"><template slot-scope="s"><div v-for="i in s.row.items" :key="i.id">item#{{i.orderItemId}} × {{i.refundQty}} @ ¥{{money(i.unitPrice)}}</div></template></el-table-column>
      <el-table-column label="操作" width="150">
        <template slot-scope="s">
          <el-button v-if="s.row.apply.status==='APPLYING'" size="mini" @click="beginEdit(s.row)">编辑</el-button>
          <el-button v-if="s.row.apply.status==='APPLYING'" size="mini" type="danger" @click="cancel(s.row)">撤销</el-button>
          <span v-if="s.row.apply.status!=='APPLYING'">-</span>
        </template>
      </el-table-column>
    </el-table>
    </div>
    <el-dialog title="编辑待审核退款申请" :visible.sync="dialog" width="560px">
      <div v-for="i in editItems" :key="i.id" style="margin-bottom:12px">item#{{i.orderItemId}}：<el-input-number v-model="quantities[i.orderItemId]" :min="0" :max="maxFor(i)" /> / 最大 {{maxFor(i)}} 件</div>
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
