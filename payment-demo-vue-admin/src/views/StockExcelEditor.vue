<template>
  <div style="height:100vh;display:flex;flex-direction:column">
    <div style="display:flex;align-items:center;justify-content:space-between;padding:8px 16px;border-bottom:1px solid #EBEEF5;background:#fff">
      <div>
        <b>编辑导入记录 #{{id}}：{{ (meta && meta.fileName) || '' }}</b>
        <el-tag v-if="meta && meta.status!=='PENDING'" size="mini" style="margin-left:8px">已入库，只读核对</el-tag>
      </div>
      <div>
        <el-button size="small" @click="back">返回（关闭页签）</el-button>
        <el-button size="small" type="primary" :loading="saving" :disabled="!sheetReady || !pending" @click="save">保存修改</el-button>
      </div>
    </div>
    <div style="position:relative;flex:1;min-height:0">
      <div v-if="!sheetReady" style="position:absolute;left:0;top:0;right:0;bottom:0;z-index:10;display:flex;align-items:center;justify-content:center;background:#fff;color:#909399">正在打开 Excel...</div>
      <div id="luckysheet-editor" style="height:100%;width:100%"/>
    </div>
  </div>
</template>

<script>
import * as XLSX from 'xlsx'
import stockApi from '../api/adminStock'

const HEADER = ['商品ID', '商品名称', '调整量']

// SheetJS AoA → Luckysheet 工作表（仅写入非空单元格）
function aoaToLuckysheet(aoa) {
  const celldata = []
  aoa.forEach((row, r) => (row || []).forEach((v, c) => {
    if (v === null || v === undefined || v === '') return
    celldata.push({ r, c, v: { v, m: String(v) } })
  }))
  return [{
    name: '库存调整', order: 0, status: 1, config: {},
    row: Math.max(aoa.length + 20, 30),
    column: Math.max(((aoa[0] || HEADER) || HEADER).length + 5, 8),
    celldata
  }]
}

const normalizeCell = cell => {
  if (cell === null || cell === undefined) return null
  if (typeof cell === 'object' && ('v' in cell)) return cell.v
  return cell
}

// Luckysheet 当前表 → AoA（getSheetData 2D 矩阵，缺失时回退 celldata）
function luckysheetToAoa() {
  const ls = window.luckysheet
  let matrix = []
  if (ls && typeof ls.getSheetData === 'function') {
    matrix = ls.getSheetData() || []
  } else if (ls && typeof ls.getAllSheets === 'function') {
    const cells = (ls.getAllSheets()[0] && ls.getAllSheets()[0].celldata) || []
    cells.forEach(c => {
      matrix[c.r] = matrix[c.r] || []
      matrix[c.r][c.c] = normalizeCell(c.v)
    })
  }
  return matrix.map(row => (row || []).map(normalizeCell))
}

// 全页签 Excel 编辑器：/admin/stock-edit/:id（由库存维护页 + 图标 / 编辑按钮新开页签进入）
export default {
  name: 'StockExcelEditor',
  data() {
    return {
      id: this.$route.params.id,
      meta: null,
      products: [],
      sheetReady: false,
      saving: false
    }
  },
  computed: {
    pending() {
      return this.meta ? this.meta.status === 'PENDING' : true
    }
  },
  async mounted() {
    let aoa = null
    let record = null
    const [productsRes, importsRes] = await Promise.all([
      stockApi.products().catch(() => ({ data: [] })),
      stockApi.imports().catch(() => ({ data: [] }))
    ])
    this.products = productsRes.data || []
    record = (importsRes.data || []).find(r => r.id === Number(this.id)) || null
    this.meta = record
    try {
      const res = await stockApi.getImportFile(this.id)
      const wb = XLSX.read(res.data, { type: 'base64' })
      aoa = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: null })
    } catch (e) {
      // 存储文件缺失（历史记录）时回退到明细快照
      aoa = [HEADER, ...this.products.map(p => {
        const it = ((record && record.items) || []).find(x => Number(x.productId) === p.id)
        return [p.id, p.title, it ? it.delta : null]
      })]
    }
    window.luckysheet.create({
      container: 'luckysheet-editor',
      data: aoaToLuckysheet(aoa || [HEADER]),
      title: (record && record.fileName) || '库存调整',
      lang: 'zh',
      showinfobar: false,
      showsheetbar: false,
      showstatisticBar: false,
      sheetFormulaBar: true,
      allowEdit: true,
      mode: 'edit'
    })
    this.sheetReady = true
  },
  methods: {
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
        const pid = Number(rawId)
        if (!Number.isInteger(pid) || !productIds.has(pid)) { issues.push(`第${line}行：商品ID ${rawId} 不存在`); return }
        if (rawDelta == null || rawDelta === '') return
        const delta = Number(rawDelta)
        if (!Number.isInteger(delta) || delta === 0) { issues.push(`第${line}行：调整量 ${rawDelta} 无效（需非 0 整数）`); return }
        items.push({ productId: pid, delta })
      })
      return { items, issues }
    },
    async save() {
      const aoa = luckysheetToAoa()
      const { items, issues } = this.validateAoa(aoa)
      if (issues.length) {
        return this.$message.error(`存在无效行，未保存：${issues[0]}${issues.length > 1 ? ` 等 ${issues.length} 处` : ''}`)
      }
      if (!items.length) return this.$message.warning('未填写任何有效调整量（调整量留空的行将被跳过）')
      const ws = XLSX.utils.aoa_to_sheet(aoa)
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, '库存调整')
      const fileBase64 = XLSX.write(wb, { bookType: 'xlsx', type: 'base64' })
      this.saving = true
      try {
        await stockApi.saveImportFile(this.id, { fileBase64, items })
        this.$message.success('导入明细已保存，可回到库存维护页点击“确认入库”执行')
        if (this.meta) this.meta.itemCount = items.length
      } catch (e) { /* 拦截器已提示 */ }
      finally { this.saving = false }
    },
    back() {
      window.close()
      setTimeout(() => this.$router.push('/admin/stock-maintenance'), 300)
    }
  }
}
</script>
