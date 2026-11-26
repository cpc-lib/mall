import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { Button, Space, Spin, Tag, message } from 'antd'
import stockApi from '@/api/adminStock'
import { loadLuckysheet } from '@/utils/luckysheet'
import { STOCK_HEADER as HEADER, validateStockAoa } from '@/utils/stockAoa'
import { aoaToLuckysheet } from '@/utils/stockAoaCore'

const normalizeCell = cell => {
  if (cell === null || cell === undefined) return null
  if (typeof cell === 'object' && 'v' in cell) return cell.v
  return cell
}

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

export default function StockExcelEditor() {
  const { id } = useParams()
  const nav = useNavigate()
  const [meta, setMeta] = useState(null)
  const [sheetReady, setSheetReady] = useState(false)
  const [saving, setSaving] = useState(false)

  const pending = meta ? meta.status === 'PENDING' : true

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      let aoa = null
      const [XLSX, ls] = await Promise.all([import('xlsx'), loadLuckysheet()])
      if (cancelled) return
      const metaRes = await stockApi.getImport(id).catch(() => ({ data: null }))
      if (cancelled) return
      const record = metaRes.data || null
      setMeta(record)
      try {
        const res = await stockApi.getImportFile(id)
        const wb = XLSX.read(res.data, { type: 'base64' })
        aoa = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: null })
      } catch (e) {
        aoa = [HEADER, ...((record && record.items) || []).map(it => [it.productId, '', it.delta])]
      }
      if (cancelled) return
      ls.create({
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
    return () => {
      cancelled = true
      window.luckysheet?.destroy?.()
    }
  }, [id])

  const save = async () => {
    const XLSX = await import('xlsx')
    const aoa = luckysheetToAoa()
    const { items, issues } = validateStockAoa(aoa)
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
