<template>
  <div>
    <h2 class="adm-page-title">订单管理</h2>
    <div class="adm-card">
    <div style="margin-bottom:16px">
      <el-select v-model="orderFilter.payStatus" placeholder="支付状态" clearable size="small" style="width:120px;margin-right:8px"><el-option label="待支付" value="UNPAID"/><el-option label="已支付" value="PAID"/></el-select>
      <el-select v-model="orderFilter.orderStatus" placeholder="订单生命周期" clearable size="small" style="width:130px;margin-right:8px"><el-option label="待支付" value="WAIT_PAY"/><el-option label="已激活" value="ACTIVE"/><el-option label="已关闭" value="CLOSED"/></el-select>
      <el-select v-model="orderFilter.fulfillmentStatus" placeholder="履约状态" clearable size="small" style="width:120px;margin-right:8px"><el-option label="待发货" value="WAIT_SHIP"/><el-option label="已发货" value="SHIPPED"/><el-option label="已收货" value="RECEIVED"/><el-option label="已取消" value="CANCELLED"/></el-select>
      <el-input v-model.trim="orderFilter.orderNo" placeholder="订单号" clearable size="small" style="width:190px;margin-right:8px"/>
      <el-input v-model.trim="orderFilter.userId" placeholder="用户编号" clearable size="small" style="width:130px;margin-right:8px"/>
      <el-date-picker v-model="dateRange" type="daterange" range-separator="至" start-placeholder="开始日期" end-placeholder="结束日期" value-format="yyyy-MM-dd" size="small" style="margin-right:8px"/>
      <el-button size="small" type="primary" @click="loadAllOrders">查询</el-button>
    </div>
    <el-table :data="allOrders" style="width:100%" size="mini">
      <el-table-column label="订单号" min-width="190"><template slot-scope="s"><a @click="openDetail(s.row)" style="color:#409eff;cursor:pointer">{{s.row.order.orderNo}}</a></template></el-table-column>
      <el-table-column label="用户" width="70"><template slot-scope="s">{{s.row.order.userId}}</template></el-table-column>
      <el-table-column label="商品" min-width="160"><template slot-scope="s"><div v-for="i in s.row.items" :key="i.id">{{i.productTitle}} × {{i.quantity}}</div></template></el-table-column>
      <el-table-column label="金额" width="90"><template slot-scope="s">¥{{money(s.row.order.totalFee)}}</template></el-table-column>
      <el-table-column label="支付" width="80"><template slot-scope="s"><el-tag :type="s.row.order.payStatus==='PAID'?'success':'warning'" size="mini">{{payLabel(s.row.order.payStatus)}}</el-tag></template></el-table-column>
      <el-table-column label="订单状态" width="90"><template slot-scope="s">{{s.row.order.orderStatus}}</template></el-table-column>
      <el-table-column label="履约" width="80"><template slot-scope="s">{{fulfillmentLabel(s.row.order.fulfillmentStatus)}}</template></el-table-column>
      <el-table-column label="退款" width="80"><template slot-scope="s">{{s.row.order.refundStatus==='NONE'?'':s.row.order.refundStatus}}</template></el-table-column>
      <el-table-column label="操作" width="170">
        <template slot-scope="s">
          <el-button size="mini" @click="openDetail(s.row)">详情</el-button>
          <el-button size="mini" type="primary" plain @click="channelQuery(s.row.order.orderNo)">渠道查单</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-dialog :title="channelQueryRow ? '渠道查单 - ' + channelQueryRow.orderNo : '渠道查单'" :visible.sync="channelQueryVisible" width="640px">
      <div v-loading="channelQueryLoading">
        <el-descriptions v-if="channelQueryRow" :column="2" border size="small">
          <el-descriptions-item label="渠道">{{channelQueryRow.channelCode}}</el-descriptions-item>
          <el-descriptions-item label="渠道状态">{{channelQueryRow.channelTradeState}}</el-descriptions-item>
          <el-descriptions-item label="状态说明" :span="2">{{channelQueryRow.channelTradeStateDesc}}</el-descriptions-item>
          <el-descriptions-item label="本地订单状态">{{channelQueryRow.localOrderStatusBefore}} → {{channelQueryRow.localOrderStatusAfter}}</el-descriptions-item>
          <el-descriptions-item label="查单后支付状态">{{channelQueryRow.localPayStatusAfter}}</el-descriptions-item>
          <el-descriptions-item label="是否同步" :span="2"><el-tag :type="channelQueryRow.synced?'success':'info'" size="small">{{channelQueryRow.synced?'已推进本地订单':'本地状态未变更'}}</el-tag></el-descriptions-item>
        </el-descriptions>
        <div v-if="channelQueryRow && channelQueryRow.channelRawBody">
          <h4>渠道原始报文</h4>
          <pre style="max-height:260px;overflow:auto;background:#f5f7fa;padding:8px;font-size:12px;border-radius:4px">{{prettyRaw(channelQueryRow.channelRawBody)}}</pre>
        </div>
      </div>
      <div slot="footer">
        <el-button @click="channelQueryVisible = false">关闭</el-button>
        <el-button v-if="channelQueryRow" type="primary" :loading="channelQueryLoading" @click="channelQuery(channelQueryRow.orderNo)">重新查单</el-button>
      </div>
    </el-dialog>

    <el-drawer :title="detailRow ? '订单详情 - ' + detailRow.order.orderNo : '订单详情'" :visible.sync="detailVisible" size="760px" direction="rtl"
      :before-close="closeDetail">
      <div v-if="detailRow" v-loading="detailLoading" style="padding:0 8px">
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="订单号">{{detailRow.order.orderNo}}</el-descriptions-item>
          <el-descriptions-item label="用户ID">{{detailRow.order.userId}}</el-descriptions-item>
          <el-descriptions-item label="支付状态"><el-tag :type="detailRow.order.payStatus==='PAID'?'success':'warning'" size="small">{{payLabel(detailRow.order.payStatus)}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="订单状态">{{detailRow.order.orderStatus}}</el-descriptions-item>
          <el-descriptions-item label="履约状态">{{fulfillmentLabel(detailRow.order.fulfillmentStatus)}}</el-descriptions-item>
          <el-descriptions-item label="退款状态">{{detailRow.order.refundStatus==='NONE'?'-':detailRow.order.refundStatus}}</el-descriptions-item>
          <el-descriptions-item label="订单金额">¥{{money(detailRow.order.totalFee)}}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{detailRow.order.paymentType||'-'}}</el-descriptions-item>
          <el-descriptions-item label="收货人">{{detailRow.order.receiverName||'-'}}</el-descriptions-item>
          <el-descriptions-item label="收货电话">{{detailRow.order.receiverPhone||'-'}}</el-descriptions-item>
          <el-descriptions-item label="收货地址" :span="2">{{detailRow.order.receiverAddress||'-'}}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{detailRow.order.createTime ? new Date(detailRow.order.createTime).toLocaleString() : '-'}}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{detailRow.order.paidTime ? new Date(detailRow.order.paidTime).toLocaleString() : '-'}}</el-descriptions-item>
        </el-descriptions>
        <h4>订单明细</h4>
        <el-table :data="detailRow.items" size="small" style="width:100%;margin-bottom:20px">
          <el-table-column prop="productTitle" label="商品"/>
          <el-table-column prop="quantity" label="数量" width="80"/>
          <el-table-column prop="unitPrice" label="快照单价(分)" width="110"/>
          <el-table-column label="小计(分)" width="100"><template slot-scope="s">{{(Number(s.row.unitPrice||0)*Number(s.row.quantity||0))}}</template></el-table-column>
        </el-table>
        <h4>差价退款</h4>
        <p style="font-size:12px;color:#909399">管理员手填金额，受订单可退额度约束；提交后创建退款单并冻结额度。</p>
        <div>
          <el-input-number v-model="paAmount" :min="1" placeholder="金额(分)" style="width:160px;margin-right:8px"/>
          <el-input v-model.trim="paReason" maxlength="255" placeholder="退款原因" style="width:240px;margin-right:8px"/>
          <el-button type="primary" @click="submitPriceAdjust">提交差价退款</el-button>
        </div>
      </div>
      <div slot="footer" v-if="detailRow" style="text-align:right">
        <el-button v-if="canClose" type="danger" @click="forceClose(detailRow.order.orderNo)">强制关单</el-button>
        <el-button v-if="canMarkPaid" type="primary" @click="markPaid(detailRow.order.orderNo)">标记已付</el-button>
        <el-button @click="closeDetail()">关闭</el-button>
      </div>
    </el-drawer>
  </div>
</template>
<script>
import shipmentApi from '../api/shipment'
import refundApi from '../api/refundApply'
import { PAY_LABEL, FULFILLMENT_LABEL } from '../utils/statusLabels'
export default {
  data() { return { allOrders: [], orderFilter: { payStatus: '', orderStatus: '', fulfillmentStatus: '', orderNo: '', userId: '' }, dateRange: null, detailVisible: false, detailRow: null, detailLoading: false, paAmount: 0, paReason: '差价补偿', channelQueryVisible: false, channelQueryLoading: false, channelQueryRow: null } },
  computed: {
    canClose() { const o = this.detailRow && this.detailRow.order; return o && o.payStatus === 'UNPAID' && (o.orderStatus === '未支付' || o.orderStatus === '超时已关闭') },
    canMarkPaid() { const o = this.detailRow && this.detailRow.order; return o && o.payStatus === 'UNPAID' && o.orderStatus === '未支付' }
  },
  created() { this.loadAllOrders() },
  methods: {
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    payLabel(v) { return PAY_LABEL[v] || v },
    fulfillmentLabel(v) { return FULFILLMENT_LABEL[v] || v },
    async loadAllOrders() {
      const params = {}
      if (this.orderFilter.payStatus) params.payStatus = this.orderFilter.payStatus
      if (this.orderFilter.orderStatus) params.orderStatus = this.orderFilter.orderStatus
      if (this.orderFilter.fulfillmentStatus) params.fulfillmentStatus = this.orderFilter.fulfillmentStatus
      if (this.orderFilter.orderNo) params.orderNo = this.orderFilter.orderNo
      if (this.orderFilter.userId) params.userId = this.orderFilter.userId
      if (this.dateRange && this.dateRange.length === 2) { params.startTime = this.dateRange[0]; params.endTime = this.dateRange[1] }
      try {
        const r = await shipmentApi.allOrders(params)
        const list = r.data || []
        this.allOrders = list
        if (this.detailRow) {
          const fresh = list.find(x => x.order.orderNo === this.detailRow.order.orderNo)
          if (fresh) this.detailRow = fresh
        }
      } catch (e) { this.allOrders = [] }
    },
    openDetail(row) { this.detailRow = row; this.detailVisible = true; this.paAmount = 0; this.paReason = '差价补偿' },
    closeDetail() { this.detailVisible = false },
    async forceClose(orderNo) { try { await this.$confirm('将关闭未支付订单并释放预占库存，确认操作？', '强制关单'); await shipmentApi.forceClose(orderNo); this.$message.success('订单已强制关闭'); this.loadAllOrders() } catch (e) { /* 取消 */ } },
    async markPaid(orderNo) { try { await this.$confirm('将手动标记该未支付订单为已支付并提交预占库存，确认操作？', '标记支付成功'); await shipmentApi.markPaid(orderNo); this.$message.success('订单已标记为支付成功'); this.loadAllOrders() } catch (e) { /* 取消 */ } },
    async channelQuery(orderNo) {
      this.channelQueryVisible = true; this.channelQueryLoading = true; this.channelQueryRow = null
      try {
        const r = await shipmentApi.channelQuery(orderNo)
        this.channelQueryRow = r.data
        this.loadAllOrders()
      } finally { this.channelQueryLoading = false }
    },
    prettyRaw(raw) { try { return JSON.stringify(JSON.parse(raw), null, 2) } catch (e) { return raw } },
    async submitPriceAdjust() {
      if (!this.paAmount) return this.$message.error('请填写退款金额（分）')
      await refundApi.priceAdjustment({ orderNo: this.detailRow.order.orderNo.trim(), amount: Number(this.paAmount), reason: (this.paReason || '差价补偿').trim() })
      this.$message.success('差价退款已创建并冻结额度'); this.paAmount = 0; this.loadAllOrders()
    }
  }
}
</script>
