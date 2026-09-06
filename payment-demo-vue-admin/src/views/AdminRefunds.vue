<template>
  <div>
    <h2 class="adm-page-title">退款受理</h2>
    <div class="adm-card" style="padding:8px 20px 16px">
    <el-table :data="refunds" style="width:100%">
      <el-table-column label="退款号" min-width="190"><template slot-scope="s"><a @click="openDetail(s.row)" style="color:#409eff;cursor:pointer">{{s.row.apply.refundNo}}</a></template></el-table-column>
      <el-table-column label="订单号" min-width="190"><template slot-scope="s">{{s.row.apply.orderNo}}</template></el-table-column>
      <el-table-column label="类型" width="180"><template slot-scope="s">{{typeLabel(s.row.apply.refundType)}}</template></el-table-column>
      <el-table-column label="金额" width="100"><template slot-scope="s">¥{{money(s.row.apply.refundAmount)}}</template></el-table-column>
      <el-table-column label="状态" width="110"><template slot-scope="s"><el-tag :type="s.row.apply.status==='FAILED'?'danger':s.row.apply.status==='SUCCESS'?'success':'info'" size="mini">{{statusLabel(s.row.apply.status)}}</el-tag></template></el-table-column>
      <el-table-column label="失败原因" min-width="200"><template slot-scope="s"><span v-if="s.row.apply.status==='FAILED'" style="color:#f50;font-size:12px">{{s.row.failReason || '-'}}</span><span v-else>-</span></template></el-table-column>
      <el-table-column label="明细"><template slot-scope="s"><div v-for="i in s.row.items" :key="i.id">item#{{i.orderItemId}} × {{i.refundQty}}</div></template></el-table-column>
      <el-table-column label="操作" width="90">
        <template slot-scope="s">
          <el-button size="mini" @click="openDetail(s.row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>
    </div>

    <el-drawer :title="detail ? '退款详情 - ' + detail.apply.refundNo : '退款详情'" :visible.sync="detailVisible" size="720px" direction="rtl">
      <div v-if="detail" style="padding:0 8px">
        <el-descriptions :column="2" border size="small" style="margin-bottom:16px">
          <el-descriptions-item label="退款号">{{detail.apply.refundNo}}</el-descriptions-item>
          <el-descriptions-item label="订单号">{{detail.apply.orderNo}}</el-descriptions-item>
          <el-descriptions-item label="退款类型">{{typeLabel(detail.apply.refundType)}}</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="detail.apply.status==='FAILED'?'danger':detail.apply.status==='SUCCESS'?'success':'info'" size="small">{{statusLabel(detail.apply.status)}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="退款金额">¥{{money(detail.apply.refundAmount)}}</el-descriptions-item>
          <el-descriptions-item label="申请时间">{{detail.apply.createTime ? new Date(detail.apply.createTime).toLocaleString() : '-'}}</el-descriptions-item>
          <el-descriptions-item label="退款原因" :span="2">{{detail.apply.reason || '-'}}</el-descriptions-item>
          <el-descriptions-item v-if="detail.failReason" label="失败原因" :span="2"><span style="color:#f50">{{detail.failReason}}</span></el-descriptions-item>
        </el-descriptions>
        <h4>退款明细</h4>
        <el-table :data="detail.items" size="small" style="width:100%">
          <el-table-column label="明细项ID" width="110"><template slot-scope="s">item#{{s.row.orderItemId}}</template></el-table-column>
          <el-table-column prop="refundQty" label="退款数量" width="90"/>
          <el-table-column prop="unitPrice" label="快照单价(分)" width="110"/>
          <el-table-column label="退款小计(分)" width="110"><template slot-scope="s">{{Number(s.row.unitPrice||0)*Number(s.row.refundQty||0)}}</template></el-table-column>
        </el-table>
      </div>
      <div slot="footer" v-if="detail" style="text-align:right">
        <el-button v-if="detail.apply.status==='APPLYING'" type="danger" @click="reject">拒绝</el-button>
        <el-button v-if="detail.apply.status==='APPLYING'" type="primary" @click="accept">受理</el-button>
        <el-button v-if="detail.apply.status==='APPROVED' && detail.apply.refundType==='RETURN_AND_REFUND'" type="primary" @click="confirmReturn">退货签收确认</el-button>
        <el-button v-if="detail.apply.status==='FAILED'" @click="retry">重试退款</el-button>
        <el-button v-if="detail.apply.status==='REFUNDING' || detail.apply.status==='FAILED'" @click="queryStatus">查询状态</el-button>
        <el-button @click="detailVisible=false">关闭</el-button>
      </div>
    </el-drawer>
  </div>
</template>
<script>
import refundApi from '../api/refundApply'
import { REFUND_STATUS_LABEL, REFUND_TYPE_LABEL } from '../utils/statusLabels'
export default {
  data() { return { refunds: [], detail: null, detailVisible: false } },
  created() { this.load() },
  methods: {
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    typeLabel(v) { return REFUND_TYPE_LABEL[v] || v },
    statusLabel(v) { return REFUND_STATUS_LABEL[v] || v },
    async load() {
      const r = await refundApi.all()
      const list = r.data || []
      this.refunds = list
      if (this.detail) this.detail = list.find(x => x.apply.refundNo === this.detail.apply.refundNo) || this.detail
    },
    openDetail(row) { this.detail = row; this.detailVisible = true },
    async accept() { await refundApi.accept(this.detail.apply.refundNo, '管理员受理'); this.$message.success('已受理'); await this.load() },
    async reject() { await refundApi.reject(this.detail.apply.refundNo, '管理员拒绝'); this.$message.success('已拒绝并释放冻结额度'); await this.load() },
    async confirmReturn() {
      try {
        await this.$confirm('确认已收到退货且质检通过？确认后回补库存并发起渠道退款。', '退货签收确认')
        await refundApi.confirmReturn(this.detail.apply.refundNo, '退货签收质检通过')
        this.$message.success('已签收确认'); await this.load()
      } catch (e) { /* 取消 */ }
    },
    async retry() { await refundApi.retry(this.detail.apply.refundNo); this.$message.success('已重新发起渠道退款'); await this.load() },
    async queryStatus() {
      const loading = this.$loading({ text: '查询渠道退款状态中...' })
      try {
        const r = await refundApi.queryStatus(this.detail.apply.refundNo)
        this.$message.success('查询完成，状态：' + (r.data && r.data.apply ? r.data.apply.status : '-'))
        await this.load()
      } catch (e) {
        this.$message.error('查询失败：' + (e && e.message ? e.message : '渠道接口异常'))
      } finally { loading.close() }
    }
  }
}
</script>
