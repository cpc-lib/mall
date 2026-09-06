<template>
  <div>
    <h2 class="adm-page-title">商品库存</h2>
    <div class="adm-card">
    <div style="margin-bottom:16px"><el-button type="primary" size="small" @click="pcVisible=true">新增商品</el-button></div>
    <el-table :data="products" style="width:100%">
      <el-table-column prop="id" label="ID" width="60"/>
      <el-table-column prop="title" label="商品" min-width="140"><template slot-scope="s"><a @click="openDetail(s.row)" style="cursor:pointer;color:#409eff">{{s.row.title}}</a></template></el-table-column>
      <el-table-column prop="price" label="价格(分)" width="90"/>
      <el-table-column prop="stock" label="可用库存" width="90"/>
      <el-table-column prop="lockedStock" label="锁定库存" width="90"/>
      <el-table-column prop="soldStock" label="已售库存" width="90"/>
      <el-table-column label="状态" width="100"><template slot-scope="s"><el-tag :type="s.row.productStatus==='ENABLED'?'success':'info'">{{s.row.productStatus}}</el-tag></template></el-table-column>
      <el-table-column label="库存调整" width="260"><template slot-scope="s"><el-input-number v-model="stockDelta[s.row.id]" size="small" style="width:130px" placeholder="±数量"/><el-button size="mini" type="primary" style="margin-left:8px" @click="adjustStock(s.row)">确认调整</el-button></template></el-table-column>
      <el-table-column label="上下架" width="100"><template slot-scope="s"><el-button size="mini" @click="setStatus(s.row)">{{s.row.productStatus==='ENABLED'?'下架':'上架'}}</el-button></template></el-table-column>
    </el-table>
    </div>
    <el-drawer :title="product.id ? '商品详情 - ' + product.title : '商品详情'" :visible.sync="detailVisible" size="720px" direction="rtl">
      <div v-loading="detailLoading" style="padding:0 8px">
        <el-descriptions :column="2" border style="margin-bottom:16px">
          <el-descriptions-item label="ID">{{product.id}}</el-descriptions-item>
          <el-descriptions-item label="商品名称">{{product.title}}</el-descriptions-item>
          <el-descriptions-item label="价格">¥{{((product.price||0)/100).toFixed(2)}}（{{product.price}}分）</el-descriptions-item>
          <el-descriptions-item label="状态"><el-tag :type="product.productStatus==='ENABLED'?'success':'info'" size="small">{{product.productStatus}}</el-tag></el-descriptions-item>
          <el-descriptions-item label="可用库存">{{product.stock}}</el-descriptions-item>
          <el-descriptions-item label="锁定库存">{{product.lockedStock}}</el-descriptions-item>
          <el-descriptions-item label="已售库存">{{product.soldStock}}</el-descriptions-item>
          <el-descriptions-item label="创建时间">{{product.createTime||'-'}}</el-descriptions-item>
          <el-descriptions-item label="更新时间">{{product.updateTime||'-'}}</el-descriptions-item>
        </el-descriptions>
        <div style="margin-bottom:16px">
          <span>调整量（正数补货，负数扣减）：</span>
          <el-input-number v-model="drawerDelta" :step="1" placeholder="±数量" style="width:150px;margin:0 8px"/>
          <el-button type="primary" size="small" @click="drawerAdjust">确认调整</el-button>
          <el-button size="small" :type="product.productStatus==='ENABLED'?'danger':'primary'" @click="drawerToggle">{{product.productStatus==='ENABLED'?'下架':'上架'}}</el-button>
        </div>
        <h4>库存操作日志（最近20条）</h4>
        <el-table :data="logs" size="small" style="width:100%">
          <el-table-column prop="createTime" label="时间" width="165"/>
          <el-table-column prop="bizNo" label="业务单号" width="210" show-overflow-tooltip/>
          <el-table-column prop="operationType" label="类型" width="130"/>
          <el-table-column label="状态" width="80"><template slot-scope="s"><el-tag :type="s.row.operationStatus==='SUCCESS'?'success':s.row.operationStatus==='NEED_MANUAL'?'danger':'warning'" size="mini">{{s.row.operationStatus}}</el-tag></template></el-table-column>
          <el-table-column label="调整量" width="80"><template slot-scope="s"><span :style="{color:(s.row.availableDelta||0)>0?'#67C23A':(s.row.availableDelta||0)<0?'#F56C6C':'#909399'}">{{s.row.availableDelta==null?'-':(s.row.availableDelta>0?'+':'')+s.row.availableDelta}}</span></template></el-table-column>
          <el-table-column prop="orderNo" label="关联订单" min-width="180" show-overflow-tooltip/>
        </el-table>
      </div>
    </el-drawer>
    <el-drawer title="新增商品" :visible.sync="pcVisible" size="480px" direction="rtl">
      <el-form label-position="top" style="padding:0 8px">
        <el-form-item label="商品名称">
          <el-input v-model.trim="pcForm.title" maxlength="100" placeholder="请输入商品名称"/>
        </el-form-item>
        <el-form-item label="价格（分）">
          <el-input-number v-model="pcForm.price" :min="1" style="width:100%" placeholder="如 1000 表示 ¥10.00"/>
        </el-form-item>
        <el-form-item label="初始库存">
          <el-input-number v-model="pcForm.stock" :min="0" style="width:100%"/>
        </el-form-item>
      </el-form>
      <div slot="footer" style="text-align:right">
        <el-button @click="pcVisible=false">取消</el-button>
        <el-button type="primary" @click="createProduct">创建</el-button>
      </div>
    </el-drawer>
  </div>
</template>
<script>
import stockApi from '../api/adminStock'
export default {
  data() { return { products: [], stockDelta: {}, pcVisible: false, pcForm: { title: '', price: 100, stock: 100 }, detailVisible: false, detailLoading: false, detailId: null, product: {}, logs: [], drawerDelta: null } },
  created() { this.load() },
  methods: {
    async load() { const r = await stockApi.products(); this.products = r.data || [] },
    async openDetail(row) {
      this.detailId = row.id; this.detailVisible = true; this.detailLoading = true; this.product = {}; this.logs = []; this.drawerDelta = null
      try {
        const res = await stockApi.getProduct(row.id)
        this.product = res.data.product || {}
        this.logs = res.data.logs || []
      } catch (e) { this.$message.error('加载失败') }
      finally { this.detailLoading = false }
    },
    async adjustStock(row) { const delta = this.stockDelta[row.id]; if (!delta) return this.$message.error('请输入库存调整量'); await stockApi.adjustStock(row.id, delta); this.$message.success('库存已更新'); this.stockDelta = { ...this.stockDelta, [row.id]: undefined }; await this.load() },
    async setStatus(row) { await stockApi.setStatus(row.id, row.productStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'); this.$message.success('状态已更新'); await this.load() },
    async drawerAdjust() {
      if (!this.drawerDelta || this.drawerDelta === 0) return this.$message.warning('请输入调整量')
      try { await stockApi.adjustStock(this.detailId, this.drawerDelta); this.$message.success('库存已调整'); this.drawerDelta = null; await this.openDetail({ id: this.detailId }); await this.load() }
      catch (e) { this.$message.error('调整失败') }
    },
    async drawerToggle() {
      const ns = this.product.productStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
      try { await stockApi.setStatus(this.detailId, ns); this.$message.success(ns === 'ENABLED' ? '已上架' : '已下架'); await this.openDetail({ id: this.detailId }); await this.load() }
      catch (e) { this.$message.error('操作失败') }
    },
    async createProduct() {
      if (!this.pcForm.title || !this.pcForm.price || this.pcForm.price <= 0) return this.$message.error('请填写商品名称和有效价格')
      await stockApi.createProduct({ title: this.pcForm.title.trim(), price: this.pcForm.price, stock: this.pcForm.stock || 0 })
      this.$message.success('商品已创建'); this.pcVisible = false; this.pcForm = { title: '', price: 100, stock: 100 }; await this.load()
    }
  }
}
</script>
