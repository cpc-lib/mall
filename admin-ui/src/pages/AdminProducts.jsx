import { useEffect, useState } from 'react'
import {
  Button, Card, Descriptions, Divider, Drawer, Input, InputNumber,
  message, Radio, Select, Space, Spin, Table, Tabs, Tag, Typography
} from 'antd'
import stockApi from '@/api/adminStock'
import { PRODUCT_STATUS_COLOR, OPERATION_STATUS_COLOR, OPERATION_STATUS_LABEL } from '@/utils/statusLabels'
import { buildSkuMatrix, parseSpecValuesInput } from '@/utils/spuMatrix'

const EMPTY_SPU_FORM = () => ({
  spuId: null,
  spuCode: '',
  title: '',
  subtitle: '',
  categoryName: '',
  brandName: '',
  mainImage: '',
  description: '',
  specMode: 'SINGLE',
  attrs: [{ attrName: '', attrValue: '' }],
  specGroups: [{ specName: '', valuesText: '' }],
  skus: [{ skuId: null, skuCode: '', price: 100, stock: 100, productStatus: 'ENABLED', specs: [], specText: '' }]
})

export default function AdminProducts() {
  const [activeTab, setActiveTab] = useState('spu')

  // --- SPU 列表与编辑器状态 ---
  const [spus, setSpus] = useState([])
  const [spuLoading, setSpuLoading] = useState(false)
  const [spuPage, setSpuPage] = useState(1)
  const [spuSize, setSpuSize] = useState(20)
  const [spuTotal, setSpuTotal] = useState(0)
  const [spuKeyword, setSpuKeyword] = useState('')
  const [spuDrawerOpen, setSpuDrawerOpen] = useState(false)
  const [spuSaving, setSpuSaving] = useState(false)
  const [spuForm, setSpuForm] = useState(EMPTY_SPU_FORM)

  // --- SKU 四桶库存状态 ---
  const [products, setProducts] = useState([])
  const [productLoading, setProductLoading] = useState(false)
  const [productPage, setProductPage] = useState(1)
  const [productSize, setProductSize] = useState(20)
  const [productTotal, setProductTotal] = useState(0)
  const [stockDelta, setStockDelta] = useState({})
  const [pcVisible, setPcVisible] = useState(false)
  const [pcForm, setPcForm] = useState({ title: '', price: 100, stock: 100 })
  const [detailId, setDetailId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [drawerDelta, setDrawerDelta] = useState(null)
  const [logs, setLogs] = useState([])
  const [logPage, setLogPage] = useState(1)
  const [logTotal, setLogTotal] = useState(0)
  const [logLoading, setLogLoading] = useState(false)

  const LOG_PAGE_SIZE = 8

  const loadSpus = (nextPage = spuPage, nextSize = spuSize, kw = spuKeyword) => {
    setSpuLoading(true)
    stockApi.spuPage({ page: nextPage, size: nextSize, keyword: kw?.trim() || undefined }).then(r => {
      const data = r.data || {}
      setSpus(data.records || [])
      setSpuTotal(data.total || 0)
      setSpuPage(data.page || nextPage)
      setSpuSize(data.size || nextSize)
    }).catch(() => { /* 拦截器已提示 */ })
      .finally(() => setSpuLoading(false))
  }

  const loadProducts = (nextPage = productPage, nextSize = productSize) => {
    setProductLoading(true)
    stockApi.productPage({ page: nextPage, size: nextSize }).then(r => {
      const data = r.data || {}
      setProducts(data.records || [])
      setProductTotal(data.total || 0)
      setProductPage(data.page || nextPage)
      setProductSize(data.size || nextSize)
    }).catch(() => { /* 拦截器已提示 */ })
      .finally(() => setProductLoading(false))
  }

  useEffect(() => { loadSpus(1, 20, ''); loadProducts(1, 20) }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const openCreateSpu = () => {
    setSpuForm(EMPTY_SPU_FORM())
    setSpuDrawerOpen(true)
  }

  const openEditSpu = async (spuId) => {
    try {
      const r = await stockApi.getSpu(spuId)
      const d = r.data
      if (!d?.spu) return
      const groups = (d.specGroups || []).map(g => ({
        specName: g.specName,
        valuesText: (g.values || []).map(v => v.specValue).join(', ')
      }))
      const skus = (d.skus || []).map(s => {
        let specs = []
        if (s.specJson) {
          try {
            const parsed = JSON.parse(s.specJson)
            specs = parsed.map(x => ({ specName: x.name, specValue: x.value }))
          } catch { specs = [] }
        }
        return {
          skuId: s.id,
          skuCode: s.skuCode || '',
          price: s.price,
          stock: s.stock,
          lockedStock: s.lockedStock || 0,
          soldStock: s.soldStock || 0,
          lostStock: s.lostStock || 0,
          productStatus: s.productStatus || 'ENABLED',
          specs,
          specText: s.specText || ''
        }
      })
      setSpuForm({
        spuId: d.spu.id,
        spuCode: d.spu.spuCode || '',
        title: d.spu.title || '',
        subtitle: d.spu.subtitle || '',
        categoryName: d.spu.categoryName || '',
        brandName: d.spu.brandName || '',
        mainImage: d.spu.mainImage || '',
        description: d.spu.description || '',
        specMode: d.spu.specMode || 'SINGLE',
        attrs: (d.attrs && d.attrs.length > 0)
          ? d.attrs.map(a => ({ attrName: a.attrName, attrValue: a.attrValue }))
          : [{ attrName: '', attrValue: '' }],
        specGroups: groups.length > 0 ? groups : [{ specName: '', valuesText: '' }],
        skus: skus.length > 0 ? skus : EMPTY_SPU_FORM().skus
      })
      setSpuDrawerOpen(true)
    } catch { /* 拦截器已提示 */ }
  }

  const handleGenerateMatrix = () => {
    const matrix = buildSkuMatrix(spuForm.specGroups, spuForm.skus)
    if (matrix.length === 0) {
      message.warning('请先填写至少 1 组销售规格名称与规格值（逗号分隔）')
      return
    }
    setSpuForm(prev => ({ ...prev, skus: matrix }))
    message.success(`已生成 ${matrix.length} 条 SKU 组合`)
  }

  const submitSpu = async () => {
    if (!spuForm.title.trim()) return message.error('请输入商品标题')
    const attrs = (spuForm.attrs || [])
      .filter(a => a.attrName?.trim() || a.attrValue?.trim())
      .map((a, idx) => ({ attrName: a.attrName.trim(), attrValue: a.attrValue.trim(), sortOrder: idx }))
    const specGroups = spuForm.specMode === 'MULTI'
      ? (spuForm.specGroups || [])
        .filter(g => g.specName?.trim())
        .map((g, idx) => ({
          specName: g.specName.trim(),
          values: parseSpecValuesInput(g.valuesText),
          sortOrder: idx
        }))
      : []
    const payload = {
      spuCode: spuForm.spuCode?.trim() || null,
      title: spuForm.title.trim(),
      subtitle: spuForm.subtitle?.trim() || null,
      categoryName: spuForm.categoryName?.trim() || null,
      brandName: spuForm.brandName?.trim() || null,
      mainImage: spuForm.mainImage?.trim() || null,
      description: spuForm.description?.trim() || null,
      specMode: spuForm.specMode,
      attrs,
      specGroups,
      skus: (spuForm.skus || []).map(s => ({
        skuId: s.skuId || null,
        skuCode: s.skuCode?.trim() || null,
        price: Number(s.price || 0),
        stock: Number(s.stock || 0),
        productStatus: s.productStatus || 'ENABLED',
        specs: spuForm.specMode === 'MULTI' ? (s.specs || []) : []
      }))
    }
    setSpuSaving(true)
    try {
      if (spuForm.spuId) {
        await stockApi.updateSpu(spuForm.spuId, payload)
        message.success('SPU 商品已更新')
      } else {
        await stockApi.createSpu(payload)
        message.success('SPU 商品已创建')
      }
      setSpuDrawerOpen(false)
      loadSpus()
      loadProducts()
    } finally {
      setSpuSaving(false)
    }
  }

  const toggleSpuStatus = async (spu) => {
    const next = spu.spuStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    await stockApi.setSpuStatus(spu.spuId, next)
    message.success(next === 'ENABLED' ? 'SPU 已上架' : 'SPU 已下架')
    loadSpus()
    loadProducts()
  }

  // --- SKU 详情与库存调整逻辑 ---
  const loadDetail = async () => {
    if (!detailId) return
    setDetailLoading(true)
    try { const res = await stockApi.getProduct(detailId); setDetail(res.data) }
    catch { /* 拦截器已提示 */ }
    finally { setDetailLoading(false) }
  }
  const loadLogs = async (page = 1) => {
    if (!detailId) return
    setLogLoading(true)
    try {
      const res = await stockApi.transactions({ page, size: LOG_PAGE_SIZE, productId: detailId })
      setLogs(res.data?.records || [])
      setLogTotal(res.data?.total || 0)
      setLogPage(page)
    }
    catch { /* 拦截器已提示 */ }
    finally { setLogLoading(false) }
  }
  useEffect(() => { if (detailId) { setDetail(null); setDrawerDelta(null); setLogs([]); loadDetail(); loadLogs(1) } }, [detailId]) // eslint-disable-line react-hooks/exhaustive-deps

  const openDetail = p => setDetailId(p.id)
  const adjustStock = async (p, delta) => {
    if (!delta) { message.error('请输入库存调整量'); return }
    await stockApi.adjustStock(p.id, delta); message.success('库存已更新')
    setStockDelta(s => ({ ...s, [p.id]: undefined })); loadProducts(); loadSpus()
  }
  const setStatus = async (p, productStatus) => {
    await stockApi.setStatus(p.id, productStatus); message.success('状态已更新'); loadProducts(); loadSpus()
  }

  const p = detail?.product
  const drawerAdjust = async () => {
    if (!drawerDelta || drawerDelta === 0) return message.warning('请输入调整量')
    try { await stockApi.adjustStock(detailId, drawerDelta); message.success('库存已调整'); setDrawerDelta(null); await loadDetail(); loadProducts(); loadSpus(); loadLogs(1) }
    catch { /* 拦截器已提示 */ }
  }
  const drawerToggle = async () => {
    const ns = p.productStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    try { await stockApi.setStatus(detailId, ns); message.success(ns === 'ENABLED' ? '已上架' : '已下架'); await loadDetail(); loadProducts(); loadSpus() }
    catch { /* 拦截器已提示 */ }
  }

  const spuCols = [
    { title: 'SPU ID', dataIndex: 'spuId', width: 80 },
    {
      title: '商品信息', dataIndex: 'title', render: (text, r) => (
        <div>
          <a style={{ fontWeight: 600 }} onClick={() => openEditSpu(r.spuId)}>{text}</a>
          <div style={{ fontSize: 12, color: '#8c8c8c' }}>
            {[r.spuCode, r.brandName, r.categoryName].filter(Boolean).join(' · ') || '-'}
          </div>
        </div>
      )
    },
    {
      title: '规格模式', dataIndex: 'specMode', width: 100,
      render: (v, r) => <Tag color={v === 'MULTI' ? 'blue' : 'default'}>{v === 'MULTI' ? `多规格 (${r.skuCount})` : '单规格'}</Tag>
    },
    {
      title: '基础属性', dataIndex: 'attrSummary', width: 220,
      render: list => (list && list.length > 0)
        ? <Space wrap size={[4, 4]}>{list.slice(0, 3).map((t, i) => <Tag key={i} style={{ margin: 0 }}>{t}</Tag>)}</Space>
        : <span style={{ color: '#bfbfbf' }}>-</span>
    },
    {
      title: '价格区间', width: 140,
      render: (_, r) => r.maxPrice > r.minPrice
        ? `¥${(r.minPrice / 100).toFixed(2)} ~ ¥${(r.maxPrice / 100).toFixed(2)}`
        : `¥${((r.minPrice || 0) / 100).toFixed(2)}`
    },
    {
      title: '总库存 (可用/锁/售/损)', width: 170,
      render: (_, r) => `${r.totalAvailableStock || 0} / ${r.totalLockedStock || 0} / ${r.totalSoldStock || 0} / ${r.totalLostStock || 0}`
    },
    {
      title: '状态', dataIndex: 'spuStatus', width: 90,
      render: v => <Tag color={PRODUCT_STATUS_COLOR[v] || 'default'}>{v}</Tag>
    },
    {
      title: '操作', width: 160,
      render: (_, r) => (
        <Space>
          <Button size="small" onClick={() => openEditSpu(r.spuId)}>编辑</Button>
          <Button size="small" danger={r.spuStatus === 'ENABLED'} onClick={() => toggleSpuStatus(r)}>
            {r.spuStatus === 'ENABLED' ? '下架' : '上架'}
          </Button>
        </Space>
      )
    }
  ]

  const productCols = [
    { title: 'SKU ID', dataIndex: 'id', width: 80 },
    {
      title: '商品 / 规格', dataIndex: 'title', render: (text, r) => (
        <div>
          <a onClick={() => openDetail(r)}>{text}</a>
          {r.specText && <Tag color="blue" style={{ marginLeft: 8 }}>{r.specText}</Tag>}
          {r.skuCode && <div style={{ fontSize: 12, color: '#8c8c8c' }}>SKU: {r.skuCode} (SPU #{r.spuId || '-'})</div>}
        </div>
      )
    },
    { title: '价格(分)', dataIndex: 'price', width: 90 },
    { title: '可用库存', dataIndex: 'stock', width: 85 },
    { title: '锁定库存', dataIndex: 'lockedStock', width: 85 },
    { title: '已售库存', dataIndex: 'soldStock', width: 85 },
    { title: '丢失库存', dataIndex: 'lostStock', width: 85, render: v => <Tag color={v > 0 ? 'orange' : 'default'}>{v || 0}</Tag> },
    {
      title: 'SKU状态', dataIndex: 'productStatus', width: 120,
      render: (v, r) => (
        <Space direction="vertical" size={2}>
          <Tag color={PRODUCT_STATUS_COLOR[v] || 'default'}>{v}</Tag>
          {r.spuStatus === 'DISABLED' && <Tag color="red" style={{ fontSize: 11 }}>所属SPU已下架</Tag>}
        </Space>
      )
    },
    { title: '库存调整', width: 210, render: (_, r) => <Space><InputNumber value={stockDelta[r.id]} placeholder="±数量" onChange={v2 => setStockDelta(s => ({ ...s, [r.id]: v2 }))} style={{ width: 100 }} /><Button type="primary" onClick={() => adjustStock(r, stockDelta[r.id])}>调整</Button></Space> },
    { title: '上下架', width: 90, render: (_, r) => <Button onClick={() => setStatus(r, r.productStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED')}>{r.productStatus === 'ENABLED' ? '下架' : '上架'}</Button> }
  ]

  const BIZ_TYPE_LABEL = {
    MANUAL_ADJUST: '手工调整', ORDER_RESERVE: '下单预占', ORDER_COMMIT: '支付提交',
    ORDER_SOLD: '确认收货结转', ORDER_RELEASE: '关单释放', REFUND_RESTOCK: '退款回补', REFUND_LOST: '仅退款货损'
  }
  const deltaCell = v => v == null || v === 0
    ? <span style={{ color: '#bfbfbf' }}>-</span>
    : <span style={{ color: v > 0 ? '#52c41a' : '#f5222d' }}>{v > 0 ? `+${v}` : v}</span>
  const logCols = [
    { title: '时间', dataIndex: 'createTime', width: 150, render: v => v ? new Date(v).toLocaleString() : '-' },
    { title: '类型', dataIndex: 'operationType', width: 104, render: v => BIZ_TYPE_LABEL[v] || v },
    { title: '状态', dataIndex: 'operationStatus', width: 66, render: v => <Tag color={OPERATION_STATUS_COLOR[v] || 'red'}>{OPERATION_STATUS_LABEL[v] || v}</Tag> },
    { title: '可用', dataIndex: 'availableDelta', width: 52, align: 'center', render: deltaCell },
    { title: '锁定', dataIndex: 'lockedDelta', width: 52, align: 'center', render: deltaCell },
    { title: '已售', dataIndex: 'soldDelta', width: 52, align: 'center', render: deltaCell },
    { title: '丢失', dataIndex: 'lostDelta', width: 52, align: 'center', render: v => v ? <Tag color="orange" style={{ margin: 0 }}>{v > 0 ? `+${v}` : v}</Tag> : <span style={{ color: '#bfbfbf' }}>-</span> },
    { title: '业务单号', dataIndex: 'bizNo', ellipsis: true }
  ]

  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>商品与库存管理</Typography.Title>
    <Card>
      <Tabs activeKey={activeTab} onChange={setActiveTab} items={[
        {
          key: 'spu',
          label: 'SPU 商品与属性规格',
          children: (
            <div>
              <Space style={{ marginBottom: 16 }}>
                <Input.Search
                  placeholder="搜索 SPU 标题 / 编码 / 品牌 / 分类"
                  value={spuKeyword}
                  onChange={e => setSpuKeyword(e.target.value)}
                  onSearch={v => loadSpus(1, spuSize, v)}
                  style={{ width: 280 }}
                  allowClear
                />
                <Button type="primary" onClick={openCreateSpu}>新建 SPU 商品</Button>
              </Space>
              <Table
                rowKey="spuId"
                dataSource={spus}
                columns={spuCols}
                loading={spuLoading}
                pagination={{
                  current: spuPage,
                  pageSize: spuSize,
                  total: spuTotal,
                  showSizeChanger: true,
                  showTotal: t => `共 ${t} 条`,
                  onChange: (p2, s2) => loadSpus(p2, s2, spuKeyword)
                }}
              />
            </div>
          )
        },
        {
          key: 'sku',
          label: 'SKU 四桶库存',
          children: (
            <div>
              <Button type="primary" style={{ marginBottom: 16 }} onClick={() => setPcVisible(true)}>快捷新增单规格商品</Button>
              <Table rowKey="id" dataSource={products} columns={productCols} loading={productLoading}
                pagination={{
                  current: productPage,
                  pageSize: productSize,
                  total: productTotal,
                  showSizeChanger: true,
                  pageSizeOptions: [10, 20, 50, 100],
                  showTotal: t => `共 ${t} 条`,
                  onChange: (p2, s2) => loadProducts(p2, s2)
                }} />
            </div>
          )
        }
      ]} />

      {/* SPU 新建 / 编辑抽屉 */}
      <Drawer
        open={spuDrawerOpen}
        width={820}
        onClose={() => setSpuDrawerOpen(false)}
        title={spuForm.spuId ? `编辑 SPU #${spuForm.spuId}` : '新建 SPU 商品'}
        footer={
          <div style={{ textAlign: 'right' }}>
            <Button style={{ marginRight: 8 }} onClick={() => setSpuDrawerOpen(false)}>取消</Button>
            <Button type="primary" loading={spuSaving} onClick={submitSpu}>保存 SPU</Button>
          </div>
        }
      >
        <Space direction="vertical" style={{ width: '100%' }} size={12}>
          <Space style={{ width: '100%' }} size={12}>
            <div style={{ flex: 2 }}>
              <label style={{ display: 'block', marginBottom: 4 }}>商品标题 *</label>
              <Input value={spuForm.title} onChange={e => setSpuForm(f => ({ ...f, title: e.target.value }))} placeholder="如：分布式高并发架构实战课" />
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', marginBottom: 4 }}>SPU 编码</label>
              <Input value={spuForm.spuCode} onChange={e => setSpuForm(f => ({ ...f, spuCode: e.target.value }))} placeholder="如 SPU-ARCH-01" />
            </div>
          </Space>
          <Space style={{ width: '100%' }} size={12}>
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', marginBottom: 4 }}>分类</label>
              <Input value={spuForm.categoryName} onChange={e => setSpuForm(f => ({ ...f, categoryName: e.target.value }))} placeholder="如：后端架构" />
            </div>
            <div style={{ flex: 1 }}>
              <label style={{ display: 'block', marginBottom: 4 }}>品牌</label>
              <Input value={spuForm.brandName} onChange={e => setSpuForm(f => ({ ...f, brandName: e.target.value }))} placeholder="如：Ivera 学堂" />
            </div>
          </Space>
          <div>
            <label style={{ display: 'block', marginBottom: 4 }}>副标题</label>
            <Input value={spuForm.subtitle} onChange={e => setSpuForm(f => ({ ...f, subtitle: e.target.value }))} placeholder="简要卖点说明" />
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: 4 }}>主图地址（可选）</label>
            <Input value={spuForm.mainImage} onChange={e => setSpuForm(f => ({ ...f, mainImage: e.target.value }))} placeholder="https://... 或留空使用默认商品图" />
          </div>
          <div>
            <label style={{ display: 'block', marginBottom: 4 }}>商品描述</label>
            <Input.TextArea rows={2} value={spuForm.description} onChange={e => setSpuForm(f => ({ ...f, description: e.target.value }))} />
          </div>

          <Divider orientation="left" style={{ margin: '8px 0' }}>基础展示属性（SPU 级参数，不拆分库存）</Divider>
          {(spuForm.attrs || []).map((attr, idx) => (
            <Space key={idx} style={{ width: '100%' }}>
              <Input placeholder="属性名（如：交付形式）" value={attr.attrName} style={{ width: 180 }}
                onChange={e => setSpuForm(f => ({ ...f, attrs: f.attrs.map((x, i) => i === idx ? { ...x, attrName: e.target.value } : x) }))} />
              <Input placeholder="属性值（如：录播视频 + 源码仓库）" value={attr.attrValue} style={{ width: 360 }}
                onChange={e => setSpuForm(f => ({ ...f, attrs: f.attrs.map((x, i) => i === idx ? { ...x, attrValue: e.target.value } : x) }))} />
              <Button size="small" danger onClick={() => setSpuForm(f => ({ ...f, attrs: f.attrs.filter((_, i) => i !== idx) }))}>删除</Button>
            </Space>
          ))}
          <Button size="small" type="dashed" onClick={() => setSpuForm(f => ({ ...f, attrs: [...f.attrs, { attrName: '', attrValue: '' }] }))}>
            + 添加基础属性
          </Button>

          <Divider orientation="left" style={{ margin: '8px 0' }}>规格模式与 SKU 列表</Divider>
          <Radio.Group
            value={spuForm.specMode}
            onChange={e => {
              const mode = e.target.value
              setSpuForm(f => ({
                ...f,
                specMode: mode,
                skus: mode === 'SINGLE'
                  ? [f.skus[0] ? { ...f.skus[0], specs: [], specText: '' } : { skuId: null, skuCode: '', price: 100, stock: 100, productStatus: 'ENABLED', specs: [], specText: '' }]
                  : f.skus
              }))
            }}
          >
            <Radio value="SINGLE">单规格（SINGLE）</Radio>
            <Radio value="MULTI">多规格（MULTI，最多 3 组销售规格）</Radio>
          </Radio.Group>

          {spuForm.specMode === 'MULTI' && (
            <div style={{ background: '#fafafa', padding: 12, borderRadius: 6 }}>
              {(spuForm.specGroups || []).map((g, idx) => (
                <Space key={idx} style={{ width: '100%', marginBottom: 8 }}>
                  <Input placeholder="规格组名（如：版本）" value={g.specName} style={{ width: 160 }}
                    onChange={e => setSpuForm(f => ({ ...f, specGroups: f.specGroups.map((x, i) => i === idx ? { ...x, specName: e.target.value } : x) }))} />
                  <Input placeholder="规格值，用逗号分隔（如：标准版, 进阶版）" value={g.valuesText} style={{ width: 380 }}
                    onChange={e => setSpuForm(f => ({ ...f, specGroups: f.specGroups.map((x, i) => i === idx ? { ...x, valuesText: e.target.value } : x) }))} />
                  <Button size="small" danger onClick={() => setSpuForm(f => ({ ...f, specGroups: f.specGroups.filter((_, i) => i !== idx) }))}>删除</Button>
                </Space>
              ))}
              <Space>
                <Button size="small" type="dashed" disabled={(spuForm.specGroups || []).length >= 3}
                  onClick={() => setSpuForm(f => ({ ...f, specGroups: [...f.specGroups, { specName: '', valuesText: '' }] }))}>
                  + 添加规格组
                </Button>
                <Button size="small" type="primary" ghost onClick={handleGenerateMatrix}>生成 / 同步 SKU 矩阵</Button>
              </Space>
            </div>
          )}

          <Table
            size="small"
            pagination={false}
            rowKey={(r, idx) => r.skuId ? `sku-${r.skuId}` : `new-${idx}`}
            dataSource={spuForm.skus}
            columns={[
              ...(spuForm.specMode === 'MULTI' ? [{ title: '规格组合', dataIndex: 'specText', width: 170 }] : []),
              {
                title: 'SKU编码', dataIndex: 'skuCode', width: 140,
                render: (v, _, idx) => (
                  <Input size="small" value={v} placeholder="可选"
                    onChange={e => setSpuForm(f => ({ ...f, skus: f.skus.map((s, i) => i === idx ? { ...s, skuCode: e.target.value } : s) }))} />
                )
              },
              {
                title: '价格(分)', dataIndex: 'price', width: 110,
                render: (v, _, idx) => (
                  <InputNumber size="small" min={1} value={v} style={{ width: 96 }}
                    onChange={val => setSpuForm(f => ({ ...f, skus: f.skus.map((s, i) => i === idx ? { ...s, price: val || 1 } : s) }))} />
                )
              },
              {
                title: '库存', dataIndex: 'stock', width: 140,
                render: (v, r, idx) => r.skuId ? (
                  <span style={{ fontSize: 12, color: '#8c8c8c' }}>可用 {r.stock}（改库存请到SKU页）</span>
                ) : (
                  <InputNumber size="small" min={0} value={v} style={{ width: 90 }}
                    onChange={val => setSpuForm(f => ({ ...f, skus: f.skus.map((s, i) => i === idx ? { ...s, stock: val || 0 } : s) }))} />
                )
              },
              {
                title: '状态', dataIndex: 'productStatus', width: 110,
                render: (v, _, idx) => (
                  <Select size="small" value={v} style={{ width: 96 }}
                    options={[{ value: 'ENABLED', label: '上架' }, { value: 'DISABLED', label: '下架' }]}
                    onChange={val => setSpuForm(f => ({ ...f, skus: f.skus.map((s, i) => i === idx ? { ...s, productStatus: val } : s) }))} />
                )
              }
            ]}
          />
        </Space>
      </Drawer>

      {/* SKU 详情抽屉 */}
      <Drawer open={detailId != null} width={720} onClose={() => setDetailId(null)} title={p ? `SKU 详情 - ${p.title}` : 'SKU 详情'}>
        {detailLoading || !p ? <Spin style={{ display: 'block', margin: '80px auto' }} /> : <div>
          <Descriptions column={2} bordered style={{ marginBottom: 16 }}>
            <Descriptions.Item label="SKU ID">{p.id}</Descriptions.Item>
            <Descriptions.Item label="所属 SPU ID">{p.spuId || '-'}</Descriptions.Item>
            <Descriptions.Item label="商品名称">{p.title}</Descriptions.Item>
            <Descriptions.Item label="规格">{p.specText || '默认单规格'}</Descriptions.Item>
            <Descriptions.Item label="价格">¥{((p.price || 0) / 100).toFixed(2)}（{p.price}分）</Descriptions.Item>
            <Descriptions.Item label="状态"><Tag color={PRODUCT_STATUS_COLOR[p.productStatus] || 'default'}>{p.productStatus}</Tag></Descriptions.Item>
            <Descriptions.Item label="可用库存">{p.stock}</Descriptions.Item>
            <Descriptions.Item label="锁定库存">{p.lockedStock}</Descriptions.Item>
            <Descriptions.Item label="已售库存">{p.soldStock}</Descriptions.Item>
            <Descriptions.Item label="丢失库存">{p.lostStock || 0}</Descriptions.Item>
            <Descriptions.Item label="创建时间">{p.createTime ? new Date(p.createTime).toLocaleString() : '-'}</Descriptions.Item>
            <Descriptions.Item label="更新时间">{p.updateTime ? new Date(p.updateTime).toLocaleString() : '-'}</Descriptions.Item>
          </Descriptions>
          <Space style={{ marginBottom: 16 }}>
            <span>调整量（正数补货，负数扣减）：</span>
            <InputNumber value={drawerDelta} onChange={v => setDrawerDelta(v)} placeholder="±数量" style={{ width: 150 }} />
            <Button type="primary" onClick={drawerAdjust}>确认调整</Button>
            <Button danger={p.productStatus === 'ENABLED'} onClick={drawerToggle}>{p.productStatus === 'ENABLED' ? '下架' : '上架'}</Button>
          </Space>
          <h4>库存操作流水</h4>
          <Table rowKey="id" dataSource={logs} columns={logCols} size="small" loading={logLoading}
            pagination={{ current: logPage, pageSize: LOG_PAGE_SIZE, total: logTotal, onChange: p2 => loadLogs(p2), showTotal: t => `共 ${t} 条`, size: 'small' }} />
        </div>}
      </Drawer>

      {/* 快捷新增单规格商品抽屉 */}
      <Drawer open={pcVisible} width={480} onClose={() => setPcVisible(false)} title="快捷新增单规格商品"
        footer={<div style={{ textAlign: 'right' }}>
          <Button style={{ marginRight: 8 }} onClick={() => setPcVisible(false)}>取消</Button>
          <Button type="primary" onClick={async () => {
            if (!pcForm.title || !pcForm.price || pcForm.price <= 0) { message.error('请填写商品名称和有效价格'); return }
            await stockApi.createProduct({ title: pcForm.title.trim(), price: pcForm.price, stock: pcForm.stock || 0 })
            message.success('商品已创建'); setPcVisible(false); setPcForm({ title: '', price: 100, stock: 100 }); loadProducts(); loadSpus()
          }}>创建</Button>
        </div>}>
        <Space direction="vertical" style={{ width: '100%' }} size={16}>
          <div><label style={{ display: 'block', marginBottom: 6 }}>商品名称</label><Input placeholder="请输入商品名称" value={pcForm.title} onChange={e => setPcForm({ ...pcForm, title: e.target.value })} /></div>
          <div><label style={{ display: 'block', marginBottom: 6 }}>价格（分）</label><InputNumber min={1} style={{ width: '100%' }} placeholder="如 1000 表示 ¥10.00" value={pcForm.price} onChange={v => setPcForm({ ...pcForm, price: v || 0 })} /></div>
          <div><label style={{ display: 'block', marginBottom: 6 }}>初始库存</label><InputNumber min={0} style={{ width: '100%' }} value={pcForm.stock} onChange={v => setPcForm({ ...pcForm, stock: v || 0 })} /></div>
        </Space>
      </Drawer>
    </Card>
  </div>
}
