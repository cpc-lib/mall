<template>
  <div>
    <h2 class="adm-page-title">订单发货</h2>
    <div class="adm-card" style="padding:8px 20px 16px">
      <el-table :data="waitShip" style="width:100%"><el-table-column prop="orderNo" label="订单号" min-width="190"/><el-table-column prop="title" label="标题" min-width="160"/><el-table-column label="金额" width="110"><template slot-scope="s">¥{{money(s.row.totalFee)}}</template></el-table-column><el-table-column prop="paymentType" label="支付方式" width="100"/><el-table-column label="支付时间" width="170"><template slot-scope="s">{{s.row.paidTime ? new Date(s.row.paidTime).toLocaleString() : '-'}}</template></el-table-column><el-table-column label="操作" width="120"><template slot-scope="s"><el-button size="mini" type="primary" @click="ship(s.row.orderNo)">模拟发货</el-button></template></el-table-column></el-table>
    </div>
  </div>
</template>
<script>
import shipmentApi from '../api/shipment'
export default {
  data() { return { waitShip: [] } },
  async created() { await this.load() },
  methods: {
    money(v) { return (Number(v || 0) / 100).toFixed(2) },
    async load() { try { const r = await shipmentApi.waitShipList(); this.waitShip = r.data || [] } catch (e) { this.waitShip = [] } },
    async ship(orderNo) { const r = await shipmentApi.shipOrder(orderNo); this.$message.success('发货成功，运单号 ' + ((r.data && r.data.trackingNo) || '-')); await this.load() }
  }
}
</script>
