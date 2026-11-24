import { useEffect, useState } from 'react'
import { Alert, Button, Card, InputNumber, Modal, Select, Space, Table, Tag, Typography, Upload, message } from 'antd'
import stockApi from '@/api/adminStock'
import { OPERATION_STATUS_COLOR, OPERATION_STATUS_LABEL, STOCK_IMPORT_STATUS_LABEL, STOCK_IMPORT_STATUS_COLOR } from '@/utils/statusLabels'
import { BIZ_TYPES, STOCK_HEADER as HEADER, deltaCell, validateStockAoa } from '@/utils/stockAoa'

export default function StockMaintenance() {
  const [titles, setTitles] = useState({})
  const [imports, setImports] = useState([])
  const [importLoading, setImportLoading] = useState(false)
  const [templateLoading, setTemplateLoading] = useState(false)
  const [importPage, setImportPage] = useState(1)
  const [importSize, setImportSize] = useState(10)
  const [importTotal, setImportTotal] = useState(0)
  const [selectedImportId, setSelectedImportId] = useState(null)
  const [batchResult, setBatchResult] = useState(null)
  const [importReport, setImportReport] = useState(null)
  const [submitting, setSubmitting] = useState(false)

  const [filter, setFilter] = useState({ productId: null, bizType: undefined, status: undefined })
  const [logs, setLogs] = useState([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)

  const loadImports = (p = importPage, s = importSize) => {
    setImportLoading(true)
    return stockApi.imports(p, s).then(res => {
      setImports(res.data?.records || [])
      setImportTotal(res.data?.total || 0)
      setImportPage(res.data?.page || p)
      setImportSize(res.data?.size || s)
    }).catch(() => {})
      .finally(() => setImportLoading(false))
  }

  useEffect(() => {
    loadImports()
    loadLogs(1)
    const onFocus = () => loadImports()
    window.addEventListener('focus', onFocus)
    return () => window.removeEventListener('focus', onFocus)
  }, [])

  const loadLogs = async (p = 1) => {
    setLoading(true)
    try {
      const res = await stockApi.transactions({ page: p, size: 10, productId: filter.productId || undefined, bizType: filter.bizType, status: filter.status })
      setLogs(res.data?.records || [])
      setTotal(res.data?.total || 0)
      setPage(res.data?.page || p)
    } catch (e) { /* 拦截器已提示 */ }
    finally { setLoading(false) }
  }

  const titleOf = id => titles[id] || '-'

  const selectedRecord = imports.find(r => r.id === selectedImportId) || null

  const openTab = record => {
    setSelectedImportId(record.id)
    window.open(`#/admin/stock-edit/${record.id}`, '_blank')
  }

  const handleExcel = async file => {
    try {
      const XLSX = await import('xlsx')
      const wb = XLSX.read(await file.arrayBuffer(), { type: 'array' })
      const aoa = XLSX.utils.sheet_to_json(wb.Sheets[wb.SheetNames[0]], { header: 1, defval: null })
      const { items, issues } = validateStockAoa(aoa)
      if (!items.length) {
        setImportReport({ applied: 0, issues })
        message.warning('Excel 中没有可导入的有效明细，未生成导入记录')
        return false
      }
      const fileBase64 = XLSX.write(wb, { bookType: 'xlsx', type: 'base64' })
      const created = await stockApi.createImport({ fileName: file.name, fileBase64, items })
      setImportReport({ applied: items.length, issues })
      await loadImports()
      setSelectedImportId(created.data?.id ?? null)
      message.success(`已导入 ${items.length} 个商品并生成导入记录，请核对后点击确认入库`)
    } catch (e) {
      // 接口错误已由拦截器提示；这里只补充本地解析失败提示
      if (!e?.response) message.error('Excel 解析失败，请使用模板格式')
    }
    return false
  }

  // 模板需要完整商品目录：仅在用户点击下载时按分页聚合，避免页面加载拉全量。
  const loadAllProducts = async () => {
    const size = 200
    const first = await stockApi.productPage({ page: 1, size })
    const all = [...(first.data?.records || [])]
    const total = first.data?.total || all.length
    for (let p = 2; all.length < total; p += 1) {
      const r = await stockApi.productPage({ page: p, size })
      const rows = r.data?.records || []
      if (!rows.length) break
      all.push(...rows)
    }
    return all
  }

  const downloadTemplate = async () => {
    setTemplateLoading(true)
    try {
      const XLSX = await import('xlsx')
      const products = await loadAllProducts()
      const ws = XLSX.utils.json_to_sheet(
        products.map(p => ({ 商品ID: p.id, 商品名称: p.title, 调整量: null })),
        { header: HEADER }
      )
      const wb = XLSX.utils.book_new()
      XLSX.utils.book_append_sheet(wb, ws, '库存调整')
      XLSX.writeFile(wb, '批量库存调整模板.xlsx')
    } catch (e) {
      // 接口错误已由拦截器提示；这里只补充本地生成失败提示
      if (!e?.response) message.error('模板生成失败，请稍后重试')
    } finally {
      setTemplateLoading(false)
    }
  }

  const submitConfirm = () => {
    const record = imports.find(r => r.id === selectedImportId)
    if (!record) return message.warning('请先点选一条导入记录')
    if (record.status !== 'PENDING') return message.warning('该导入记录已确认入库，不能重复执行')
    Modal.confirm({
      title: `确认入库：记录 #${record.id}（${record.fileName}）`,
      content: `将按该记录已保存的 ${record.itemCount} 条明细执行库存调整；执行后记录不可重复使用。`,
      okText: '确认入库',
      onOk: async () => {
        setSubmitting(true)
        try {
          const res = await stockApi.confirmImport(record.id)
          setBatchResult(res.data)
          const ids = [...new Set((res.data?.results || []).map(v => v.productId).filter(v => v != null))]
          if (ids.length) {
            stockApi.productsByIds(ids)
              .then(r => setTitles(Object.fromEntries((r.data || []).map(p => [p.id, p.title]))))
              .catch(() => {})
          }
          message.success(res.message || '确认入库完成')
          setSelectedImportId(null)
          loadImports()
          loadLogs(1)
        } catch (e) { /* 拦截器已提示 */ }
        finally { setSubmitting(false) }
      }
    })
  }

  const resultCols = [
    { title: '商品ID', dataIndex: 'productId', width: 90 },
    { title: '商品', width: 160, ellipsis: true, render: (_, v) => titleOf(v.productId) },
    { title: '调整量', dataIndex: 'delta', width: 90, render: v => (v > 0 ? `+${v}` : v) },
    { title: '结果', dataIndex: 'success', width: 90, render: v => <Tag color={v ? 'green' : 'red'}>{v ? '成功' : '失败'}</Tag> },
    { title: '调整后库存', dataIndex: 'stock', width: 110, render: v => v == null ? '-' : v },
    { title: '说明', dataIndex: 'message', render: v => v || '-' }
  ]

  const logCols = [
    { title: '时间', dataIndex: 'createTime', width: 165, render: v => v ? new Date(v).toLocaleString() : '-' },
    { title: '业务单号', dataIndex: 'bizNo', width: 210, ellipsis: true },
    { title: '类型', dataIndex: 'operationType', width: 170, render: v => (BIZ_TYPES.find(t => t.value === v)?.label) || v },
    { title: '状态', dataIndex: 'operationStatus', width: 85, render: v => <Tag color={OPERATION_STATUS_COLOR[v] || 'red'}>{OPERATION_STATUS_LABEL[v] || v}</Tag> },
    { title: '可用', dataIndex: 'availableDelta', width: 70, align: 'center', render: deltaCell },
    { title: '锁定', dataIndex: 'lockedDelta', width: 70, align: 'center', render: deltaCell },
    { title: '已售', dataIndex: 'soldDelta', width: 70, align: 'center', render: deltaCell },
    { title: '丢失', dataIndex: 'lostDelta', width: 70, align: 'center', render: v => v ? <Tag color="orange">{v > 0 ? `+${v}` : v}</Tag> : <span style={{ color: '#bfbfbf' }}>-</span> },
    { title: '关联订单', dataIndex: 'orderNo', width: 180, ellipsis: true, render: v => v || '-' },
    { title: '错误信息', dataIndex: 'errorMessage', ellipsis: true, render: v => v ? <span style={{ color: '#f5222d' }}>{v}</span> : '-' }
  ]

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>批量库存维护</Typography.Title>
      <Card title="导入记录" extra={(
        <Space>
          {selectedRecord
            ? <span style={{ color: '#1677ff' }}>已选：#{selectedRecord.id} {selectedRecord.fileName}（{selectedRecord.itemCount} 条）</span>
            : <span style={{ color: '#8c8c8c' }}>未选择记录</span>}
          <Button loading={templateLoading} onClick={downloadTemplate}>下载模板</Button>
          <Upload accept=".xlsx,.xls" showUploadList={false} beforeUpload={handleExcel}>
            <Button>导入 Excel</Button>
          </Upload>
          <Button type="primary" loading={submitting} onClick={submitConfirm}>确认入库</Button>
        </Space>
      )}>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 12 }}>点选记录 → 点 ＋ 或“编辑 Excel”在新页签全屏核对/编辑并保存 → 确认入库执行；已入库记录不可重复执行</Typography.Paragraph>
        {importReport && (
          <Alert style={{ marginBottom: 12 }} showIcon closable onClose={() => setImportReport(null)}
            type={importReport.issues.length ? 'warning' : 'success'}
            message={`Excel 导入：成功匹配 ${importReport.applied} 行${importReport.issues.length ? `，${importReport.issues.length} 行被跳过` : ''}`}
            description={importReport.issues.length ? importReport.issues.map((t, i) => <div key={i}>{t}</div>) : undefined} />
        )}
        <Table rowKey="id" dataSource={imports} size="small" loading={importLoading} scroll={{ y: 320 }}
          pagination={{
            current: importPage,
            pageSize: importSize,
            total: importTotal,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50, 100],
            showTotal: t => `共 ${t} 条`,
            onChange: (p, s) => loadImports(p, s)
          }}
          onRow={r => ({ onClick: () => { if (r.status === 'PENDING') setSelectedImportId(r.id) } })}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedImportId != null ? [selectedImportId] : [],
            onChange: keys => setSelectedImportId(keys.length ? keys[0] : null),
            getCheckboxProps: r => ({ disabled: r.status !== 'PENDING' })
          }}
          columns={[
            {
              title: '', width: 40,
              render: (_, r) => <a style={{ fontSize: 16, fontWeight: 700 }} title="新开页签打开全屏 Excel 编辑器" onClick={e => { e.stopPropagation(); openTab(r) }}>＋</a>
            },
            { title: '记录ID', dataIndex: 'id', width: 90 },
            { title: '文件名', dataIndex: 'fileName', ellipsis: true },
            { title: '明细条数', dataIndex: 'itemCount', width: 90 },
            { title: '状态', dataIndex: 'status', width: 100, render: v => <Tag color={STOCK_IMPORT_STATUS_COLOR[v] || 'default'}>{STOCK_IMPORT_STATUS_LABEL[v] || v}</Tag> },
            { title: '导入时间', dataIndex: 'createTime', width: 165, render: v => (v ? new Date(v).toLocaleString() : '-') },
            {
              title: '操作', width: 110,
              render: (_, r) => (r.status === 'PENDING'
                ? <Button type="link" size="small" onClick={e => { e.stopPropagation(); openTab(r) }}>{r.id === selectedImportId ? '编辑 Excel' : '选择并编辑'}</Button>
                : <span style={{ color: '#8c8c8c' }}>—</span>)
            }
          ]} />
      </Card>
      {batchResult && (
        <Card title="批量结果" extra={<Typography.Text type="secondary">成功 {batchResult.successCount} 条 / 失败 {batchResult.failCount} 条</Typography.Text>}>
          <Table rowKey={(_, i) => i} dataSource={batchResult.results} columns={resultCols} pagination={false} size="small" />
        </Card>
      )}
      <Card title="库存流水" extra={<Typography.Text type="secondary">含成功与失败记录，分页查询</Typography.Text>}>
        <Space style={{ marginBottom: 12 }}>
          <InputNumber placeholder="商品ID" min={1} style={{ width: 120 }} value={filter.productId} onChange={v => setFilter({ ...filter, productId: v })} />
          <Select placeholder="类型" allowClear style={{ width: 210 }} value={filter.bizType} onChange={v => setFilter({ ...filter, bizType: v })} options={BIZ_TYPES} />
          <Select placeholder="状态" allowClear style={{ width: 110 }} value={filter.status} onChange={v => setFilter({ ...filter, status: v })}
            options={[{ value: 'SUCCESS', label: '成功' }, { value: 'FAILED', label: '失败' }]} />
          <Button type="primary" onClick={() => loadLogs(1)}>查询</Button>
        </Space>
        <Table rowKey="id" loading={loading} dataSource={logs} columns={logCols} size="small"
          pagination={{ current: page, pageSize: 10, total, onChange: p => loadLogs(p), showTotal: t => `共 ${t} 条` }} />
      </Card>
    </div>
  )
}
