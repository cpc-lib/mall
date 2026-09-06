import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Space, Spin, Tag, message } from 'antd'
import * as XLSX from 'xlsx'
import stockApi from '@/api/adminStock'

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
    column: Math.max((aoa[0] || HEADER).length + 5, 8),
    celldata
  }]
}

const normalizeCell = cell => {
  if (cell === null || cell === undefined) return null
  if (typeof cell === 'object' && 'v' in cell) return cell.v
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
export default function StockExcelEditor() {
  const { id } = useParams()
  const nav = useNavigate()
  const [meta, setMeta] = useState(null)
  const [products, setProducts] = useState([])
  const [sheetReady, setSheetReady] = useState(false)
  const [saving, setSaving] = useState(false)

  const pending = meta ? meta.status === 'PENDING' : true

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      let aoa = null
      let record = null
      const [productsRes, importsRes] = await Promise.all([
        stockApi.products().catch(() => ({ data: [] })),
        stockApi.imports().catch(() => ({ data: [] }))
      ])
      if (cancelled) return
      const productList = productsRes.data || []
      setProducts(productList)
      record = (importsRes.data || []).find(r => r.id === Number(id)) || null
      setMeta(record)
      try {
        const res = await stockApi.getImportFile(id)
        const wb = XLSX.read(res.data, { type: 'base64' })
        aoa = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: null })
      } catch (e) {
        // 存储文件缺失（历史记录）时回退到明细快照
        aoa = [HEADER, ...productList.map(p => {
          const it = ((record && record.items) || []).find(x => Number(x.productId) === p.id)
          return [p.id, p.title, it ? it.delta : null]
        })]
      }
      if (cancelled) return
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
      setSheetReady(true)
    })()
    return () => { cancelled = true }
  }, [id])

  // Excel 内容（AoA）→ 明细：表头行跳过，商品ID 必须存在；调整量留空跳过、填了必须非 0 整数
  const validateAoa = aoa => {
    const items = []
    const issues = []
    const productIds = new Set(products.map(p => p.id))
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
  }

  const save = async () => {
    const aoa = luckysheetToAoa()
    const { items, issues } = validateAoa(aoa)
    if (issues.length) {
      return message.error(`存在无效行，未保存：${issues[0]}${issues.length > 1 ? ` 等 ${issues.length} 处` : ''}`)
    }
    if (!items.length) return message.warning('未填写任何有效调整量（调整量留空的行将被跳过）')
    const ws = XLSX.utils.aoa_to_sheet(aoa)
    const wb = XLSX.utils.book_new()
    XLSX.utils.book_append_sheet(wb, ws, '库存调整')
    const fileBase64 = XLSX.write(wb, { bookType: 'xlsx', type: 'base64' })
    setSaving(true)
    try {
      await stockApi.saveImportFile(id, { fileBase64, items })
      message.success('导入明细已保存，可回到库存维护页点击“确认入库”执行')
      setMeta(m => (m ? { ...m, itemCount: items.length } : m))
    } catch (e) { /* 拦截器已提示 */ }
    finally { setSaving(false) }
  }

  const back = () => {
    window.close()
    setTimeout(() => nav('/admin/stock-maintenance'), 300)
  }

  return (
    <div style={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '8px 16px', borderBottom: '1px solid #f0f0f0', background: '#fff' }}>
        <Space>
          <b>{meta ? `编辑导入记录 #${meta.id}：${meta.fileName || ''}` : `编辑导入记录 #${id}`}</b>
          {meta && meta.status !== 'PENDING' && <Tag>已入库，只读核对</Tag>}
        </Space>
        <Space>
          <Button onClick={back}>返回（关闭页签）</Button>
          <Button type="primary" loading={saving} disabled={!sheetReady || !pending} onClick={save}>保存修改</Button>
        </Space>
      </div>
      <div style={{ position: 'relative', flex: 1, minHeight: 0 }}>
        {!sheetReady && (
          <div style={{ position: 'absolute', left: 0, top: 0, right: 0, bottom: 0, zIndex: 10, display: 'flex', alignItems: 'center', justifyContent: 'center', background: '#fff' }}>
            <Spin tip="正在打开 Excel..." />
          </div>
        )}
        <div id="luckysheet-editor" style={{ height: '100%', width: '100%' }} />
      </div>
    </div>
  )
}
