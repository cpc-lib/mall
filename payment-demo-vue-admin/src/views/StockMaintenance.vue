<template>
  <div>
    <h2 class="adm-page-title">批量库存维护</h2>
    <div class="adm-card">
      <div class="adm-card-head">
        <span class="adm-card-title">导入记录</span>
        <span class="adm-card-sub">点选记录 → 点 ＋ 或“编辑 Excel”在新页签全屏核对/编辑并保存 → 确认入库执行；已入库记录不可重复执行</span>
        <div style="margin-left:auto">
          <span v-if="selectedRecord" style="color:#409EFF;margin-right:8px;font-size:12px">已选：#{{selectedRecord.id}} {{selectedRecord.fileName}}（{{selectedRecord.itemCount}} 条）</span>
          <span v-else style="color:#909399;margin-right:8px;font-size:12px">未选择记录</span>
          <el-button size="small" @click="downloadTemplate">下载模板</el-button>
          <el-upload :auto-upload="false" :show-file-list="false" accept=".xlsx,.xls" :on-change="handleExcel" style="display:inline-block;margin:0 8px">
            <el-button size="small">导入 Excel</el-button>
          </el-upload>
          <el-button size="small" type="primary" :loading="submitting" @click="submitConfirm">确认入库</el-button>
        </div>
      </div>
      <el-alert v-if="importReport" style="margin-bottom:12px" :closable="true" @close="importReport=null"
        :type="importReport.issues.length ? 'warning' : 'success'"
        :title="'Excel 导入：成功匹配 ' + importReport.applied + ' 行' + (importReport.issues.length ? '，' + importReport.issues.length + ' 行被跳过' : '')">
        <div v-for="(t, i) in importReport.issues" :key="i">{{t}}</div>
      </el-alert>
      <el-table :data="imports" size="small" style="width:100%" max-height="320"
        :row-class-name="importRowClass" @row-click="onImportRowClick">
        <el-table-column label="" width="45" align="center">
          <template slot-scope="s">
            <el-button type="text" style="font-size:16px;font-weight:700;padding:0" title="新开页签打开全屏 Excel 编辑器"
              @click.stop="openTab(s.row)">＋</el-button>
          </template>
        </el-table-column>
        <el-table-column label="选择" width="55" align="center">
          <template slot-scope="s">
            <el-radio v-model="selectedImportId" :label="s.row.id" :disabled="s.row.status!=='PENDING'"><span/></el-radio>
          </template>
        </el-table-column>
        <el-table-column prop="id" label="记录ID" width="90"/>
        <el-table-column prop="fileName" label="文件名" show-overflow-tooltip/>
        <el-table-column prop="itemCount" label="明细条数" width="90"/>
        <el-table-column label="状态" width="100"><template slot-scope="s"><el-tag size="mini" :type="s.row.status==='PENDING'?'warning':'info'">{{s.row.status==='PENDING'?'待确认':'已入库'}}</el-tag></template></el-table-column>
        <el-table-column label="导入时间" width="165"><template slot-scope="s">{{s.row.createTime?new Date(s.row.createTime).toLocaleString():'-'}}</template></el-table-column>
        <el-table-column label="操作" width="120">
          <template slot-scope="s">
            <el-button v-if="s.row.status==='PENDING'" type="text" size="mini" @click.stop="openTab(s.row)">{{s.row.id===selectedImportId?'编辑 Excel':'选择并编辑'}}</el-button>
            <span v-else style="color:#909399">—</span>
          </template>
        </el-table-column>
      </el-table>
    </div>
    <div class="adm-card" v-if="batchResult">
      <div class="adm-card-head"><span class="adm-card-title">批量结果</span><span class="adm-card-sub">成功 {{batchResult.successCount}} 条 / 失败 {{batchResult.failCount}} 条</span></div>
      <el-table :data="batchResult.results" size="small" style="width:100%">
        <el-table-column prop="productId" label="商品ID" width="90"/>
        <el-table-column label="商品" width="160" show-overflow-tooltip><template slot-scope="s">{{titleOf(s.row.productId)}}</template></el-table-column>
        <el-table-column prop="delta" label="调整量" width="90"><template slot-scope="s">{{s.row.delta>0?'+'+s.row.delta:s.row.delta}}</template></el-table-column>
        <el-table-column label="结果" width="90"><template slot-scope="s"><el-tag :type="s.row.success?'success':'danger'" size="mini">{{s.row.success?'成功':'失败'}}</el-tag></template></el-table-column>
        <el-table-column label="调整后库存" width="110"><template slot-scope="s">{{s.row.stock==null?'-':s.row.stock}}</template></el-table-column>
        <el-table-column prop="message" label="说明"/>
      </el-table>
    </div>
    <div class="adm-card">
      <div class="adm-card-head"><span class="adm-card-title">库存流水</span><span class="adm-card-sub">含成功与失败记录，分页查询</span></div>
      <div style="margin-bottom:12px">
        <el-input-number v-model="filter.productId" :min="1" placeholder="商品ID" size="small" style="width:130px;margin-right:8px"/>
        <el-select v-model="filter.bizType" placeholder="类型" clearable size="small" style="width:210px;margin-right:8px">
          <el-option v-for="t in bizTypes" :key="t.value" :label="t.label" :value="t.value"/>
        </el-select>
        <el-select v-model="filter.status" placeholder="状态" clearable size="small" style="width:110px;margin-right:8px">
          <el-option label="成功" value="SUCCESS"/><el-option label="失败" value="FAILED"/>
        </el-select>
        <el-button size="small" type="primary" @click="loadLogs(1)">查询</el-button>
      </div>
      <el-table v-loading="loading" :data="logs" size="small" style="width:100%">
        <el-table-column label="时间" width="165"><template slot-scope="s">{{s.row.createTime?new Date(s.row.createTime).toLocaleString():'-'}}</template></el-table-column>
        <el-table-column prop="bizNo" label="业务单号" width="200" show-overflow-tooltip/>
        <el-table-column prop="operationType" label="类型" width="140"/>
        <el-table-column label="状态" width="85"><template slot-scope="s"><el-tag :type="s.row.operationStatus==='SUCCESS'?'success':'danger'" size="mini">{{s.row.operationStatus}}</el-tag></template></el-table-column>
        <el-table-column label="调整量" width="80"><template slot-scope="s"><span :style="{color:(s.row.availableDelta||0)>0?'#67C23A':(s.row.availableDelta||0)<0?'#F56C6C':'#909399'}">{{s.row.availableDelta==null?'-':(s.row.availableDelta>0?'+':'')+s.row.availableDelta}}</span></template></el-table-column>
        <el-table-column label="关联订单" min-width="150" show-overflow-tooltip><template slot-scope="s">{{s.row.orderNo||'-'}}</template></el-table-column>
        <el-table-column label="错误信息" min-width="180" show-overflow-tooltip><template slot-scope="s"><span v-if="s.row.errorMessage" style="color:#f5222d">{{s.row.errorMessage}}</span><span v-else>-</span></template></el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px;text-align:right" background layout="total, prev, pager, next" :total="total" :page-size="10" :current-page.sync="page" @current-change="loadLogs"/>
    </div>
  </div>
</template>

<script>
import * as XLSX from 'xlsx'
import stockApi from '../api/adminStock'

const HEADER = ['商品ID', '商品名称', '调整量']

export default {
  name: 'StockMaintenance',
  data() {
    return {
      products: [],
      imports: [],
      selectedImportId: null,
      submitting: false,
      batchResult: null,
      importReport: null,
      bizTypes: [
        { value: 'MANUAL_ADJUST', label: 'MANUAL_ADJUST（手工调整）' },
        { value: 'ORDER_RESERVE', label: 'ORDER_RESERVE（下单预占）' },
        { value: 'ORDER_COMMIT', label: 'ORDER_COMMIT（支付提交）' },
        { value: 'ORDER_SOLD', label: 'ORDER_SOLD（确认收货结转）' },
        { value: 'ORDER_RELEASE', label: 'ORDER_RELEASE（关单释放）' },
        { value: 'REFUND_RESTOCK', label: 'REFUND_RESTOCK（退款回补）' }
      ],
      filter: { productId: null, bizType: null, status: null },
      logs: [],
      total: 0,
      page: 1,
      loading: false
    }
  },
  computed: {
    selectedRecord() {
      return this.imports.find(r => r.id === this.selectedImportId) || null
    }
  },
  mounted() {
    this.loadProducts()
    this.loadImports()
    this.loadLogs(1)
    // Excel 编辑在新页签中进行，回到本页签时刷新记录列表
    window.addEventListener('focus', this.onWindowFocus)
  },
  beforeDestroy() {
    window.removeEventListener('focus', this.onWindowFocus)
  },
  methods: {
    loadProducts() {
      stockApi.products().then(res => {
        this.products = res.data || []
      }).catch(() => {})
    },
    loadImports() {
      stockApi.imports().then(res => { this.imports = res.data || [] }).catch(() => {})
    },
    titleOf(id) {
      const p = this.products.find(x => x.id === id)
      return p ? p.title : '-'
    },
    // Excel 内容（AoA）→ 明细：表头行跳过，商品ID 必须存在；调整量留空跳过、填了必须非 0 整数
    validateAoa(aoa) {
      const items = []
      const issues = []
      const productIds = new Set(this.products.map(p => p.id))
      aoa.forEach((row, i) => {
        const rawId = row ? row[0] : null
        const rawDelta = row ? row[2] : null
        if (i === 0 && typeof rawId === 'string' && rawId.includes('商品')) return // 模板表头行
        if (rawId == null || rawId === '') return
        const line = i + 1
        const id = Number(rawId)
        if (!Number.isInteger(id) || !productIds.has(id)) { issues.push(`第${line}行：商品ID ${rawId} 不存在`); return }
        if (rawDelta == null || rawDelta === '') return
        const delta = Number(rawDelta)
        if (!Number.isInteger(delta) || delta === 0) { issues.push(`第${line}行：调整量 ${rawDelta} 无效（需非 0 整数）`); return }
        items.push({ productId: id, delta })
      })
      return { items, issues }
    },
    async handleExcel(file) {
      let wb
      try {
        const raw = file.raw || file
        wb = XLSX.read(await raw.arrayBuffer(), { type: 'array' })
      } catch (e) {
        this.$message.error('Excel 解析失败，请使用模板格式')
        return
      }
      const aoa = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: null })
      const { items, issues } = this.validateAoa(aoa)
      if (!items.length) {
        this.importReport = { applied: 0, issues }
        this.$message.warning('Excel 中没有可导入的有效明细，未生成导入记录')
        return
      }
      try {
        // 导入的 Excel 转标准 xlsx 存到后端目录（导入记录 {id}.xlsx）
        const fileBase64 = XLSX.write(wb, { bookType: 'xlsx', type: 'base64' })
        const created = await stockApi.createImport({ fileName: file.name, fileBase64, items })
        this.importReport = { applied: items.length, issues }
        this.loadImports()
        this.selectedImportId = (created.data && created.data.id) || null
        this.$message.success(`已导入 ${items.length} 个商品并生成导入记录，请核对后点击确认入库`)
      } catch (e) { /* 拦截器已提示 */ }
    },
    downloadTemplate() {
      const ws = XLSX.utils.json_to_sheet(
        this.products.map(p => ({ 商品ID: p.id, 商品名称: p.title, 调整量: null })),
        { header: HEADER }
      )
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, '库存调整')
      XLSX.writeFile(wb, '批量库存调整模板.xlsx')
    },
    importRowClass({ row }) {
      return row.id === this.selectedImportId ? 'current-row' : ''
    },
    onImportRowClick(row) {
      if (row.status === 'PENDING') this.selectedImportId = row.id
    },
    onWindowFocus() {
      this.loadImports()
    },
    // —— 编辑：新开页签打开全屏 Excel 编辑器（/admin/stock-edit/:id） ——
    openTab(record) {
      this.selectedImportId = record.id
      const { href } = this.$router.resolve(`/admin/stock-edit/${record.id}`)
      window.open(href, '_blank')
    },
    // —— 确认入库：作用于点选的导入记录，按其已保存明细执行 ——
    submitConfirm() {
      const record = this.imports.find(r => r.id === this.selectedImportId)
      if (!record) return this.$message.warning('请先点选一条导入记录')
      if (record.status !== 'PENDING') return this.$message.warning('该导入记录已确认入库，不能重复执行')
      this.$confirm(`将按该记录已保存的 ${record.itemCount} 条明细执行库存调整；执行后记录不可重复使用。`, `确认入库：记录 #${record.id}（${record.fileName}）`, {
        confirmButtonText: '确认入库',
        cancelButtonText: '取消',
        type: 'warning'
      }).then(async () => {
        this.submitting = true
        try {
          const res = await stockApi.confirmImport(record.id)
          this.batchResult = res.data
          this.$message.success(res.message || '确认入库完成')
          this.selectedImportId = null
          this.loadProducts()
          this.loadImports()
          this.loadLogs(1)
        } catch (e) { /* 拦截器已提示 */ }
        finally { this.submitting = false }
      }).catch(() => {})
    },
    async loadLogs(p) {
      this.loading = true
      try {
        const res = await stockApi.transactions({ page: p || 1, size: 10, productId: this.filter.productId || undefined, bizType: this.filter.bizType || undefined, status: this.filter.status || undefined })
        this.logs = (res.data && res.data.records) || []
        this.total = (res.data && res.data.total) || 0
        this.page = (res.data && res.data.page) || p || 1
      } catch (e) { /* 拦截器已提示 */ }
      finally { this.loading = false }
    }
  }
}
</script>
