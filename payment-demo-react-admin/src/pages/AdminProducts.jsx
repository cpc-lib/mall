import { useEffect, useState } from 'react'
import { Button, Descriptions, Drawer, Input, InputNumber, message, Space, Spin, Table, Tag } from 'antd'
import stockApi from '@/api/adminStock'

// 商品库存管理：列表（行内调库存/上下架）+ 新增商品 + 商品详情抽屉（信息/调库存/上下架/日志）
export default function AdminProducts() {
  const [products, setProducts] = useState([])
  const [stockDelta, setStockDelta] = useState({})
  const [pcVisible, setPcVisible] = useState(false)
  const [pcForm, setPcForm] = useState({ title: '', price: 100, stock: 100 })
  const [detailId, setDetailId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [drawerDelta, setDrawerDelta] = useState(null)

  const loadProducts = () => { stockApi.products().then(r => setProducts(r.data || [])) }
  useEffect(loadProducts, [])
  const loadDetail = async () => {
    if (!detailId) return
    setDetailLoading(true)
    try { const res = await stockApi.getProduct(detailId); setDetail(res.data) }
    catch (e) { message.error('加载失败') }
    finally { setDetailLoading(false) }
  }
  useEffect(() => { if (detailId) { setDetail(null); setDrawerDelta(null); loadDetail() } }, [detailId])

  const openDetail = p => setDetailId(p.id)
  const adjustStock = async (p, delta) => {
    if (!delta) { message.error('请输入库存调整量'); return }
    await stockApi.adjustStock(p.id, delta); message.success('库存已更新')
    setStockDelta(s => ({ ...s, [p.id]: undefined })); loadProducts()
  }
  const setStatus = async (p, productStatus) => { await stockApi.setStatus(p.id, productStatus); message.success('状态已更新'); loadProducts() }

  const p = detail?.product
  const drawerAdjust = async () => {
    if (!drawerDelta || drawerDelta === 0) return message.warning('请输入调整量')
    try { await stockApi.adjustStock(detailId, drawerDelta); message.success('库存已调整'); setDrawerDelta(null); await loadDetail(); loadProducts() }
    catch (e) { message.error(e.response?.data?.message || '调整失败') }
  }
  const drawerToggle = async () => {
    const ns = p.productStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    try { await stockApi.setStatus(detailId, ns); message.success(ns === 'ENABLED' ? '已上架' : '已下架'); await loadDetail(); loadProducts() }
    catch (e) { message.error(e.response?.data?.message || '操作失败') }
  }

  const productCols = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '商品', dataIndex: 'title', render: (text, p) => <a onClick={() => openDetail(p)}>{text}</a> },
    { title: '价格(分)', dataIndex: 'price', width: 90 },
    { title: '可用库存', dataIndex: 'stock', width: 90 },
    { title: '锁定库存', dataIndex: 'lockedStock', width: 90 },
    { title: '状态', dataIndex: 'productStatus', width: 90, render: v => <Tag color={v === 'ENABLED' ? 'green' : 'default'}>{v}</Tag> },
    { title: '库存调整', width: 220, render: (_, p) => <Space><InputNumber value={stockDelta[p.id]} placeholder="±数量" onChange={v2 => setStockDelta(s => ({ ...s, [p.id]: v2 }))} style={{ width: 110 }} /><Button type="primary" onClick={() => adjustStock(p, stockDelta[p.id])}>确认调整</Button></Space> },
    { title: '上下架', width: 150, render: (_, p) => <Button onClick={() => setStatus(p, p.productStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED')}>{p.productStatus === 'ENABLED' ? '下架' : '上架'}</Button> }
  ]
  const logCols = [
    { title: '时间', dataIndex: 'createTime', width: 165, render: v => v ? new Date(v).toLocaleString() : '-' },
    { title: '业务单号', dataIndex: 'bizNo', width: 210, ellipsis: true },
    { title: '类型', dataIndex: 'operationType', width: 130 },
    { title: '状态', dataIndex: 'operationStatus', width: 80, render: v => <Tag color={v === 'SUCCESS' ? 'green' : v === 'NEED_MANUAL' ? 'red' : 'orange'}>{v}</Tag> },
    { title: '调整量', dataIndex: 'availableDelta', width: 80, render: v => v == null ? '-' : <span style={{ color: v > 0 ? '#52c41a' : v < 0 ? '#f5222d' : '#8c8c8c' }}>{v > 0 ? `+${v}` : v}</span> },
    { title: '关联订单', dataIndex: 'orderNo', ellipsis: true }
  ]

  return <div>
    <h2 className="adm-page-title">商品库存</h2>
    <div className="adm-card">
    <Button type="primary" style={{ marginBottom: 16 }} onClick={() => setPcVisible(true)}>新增商品</Button>
    <Table rowKey="id" dataSource={products} columns={productCols} />
    <Drawer open={detailId != null} width={720} onClose={() => setDetailId(null)} title={p ? `商品详情 - ${p.title}` : '商品详情'}>
      {detailLoading || !p ? <Spin style={{ display: 'block', margin: '80px auto' }} /> : <div>
        <Descriptions column={2} bordered style={{ marginBottom: 16 }}>
          <Descriptions.Item label="ID">{p.id}</Descriptions.Item>
          <Descriptions.Item label="商品名称">{p.title}</Descriptions.Item>
          <Descriptions.Item label="价格">¥{((p.price || 0) / 100).toFixed(2)}（{p.price}分）</Descriptions.Item>
          <Descriptions.Item label="状态"><Tag color={p.productStatus === 'ENABLED' ? 'green' : 'default'}>{p.productStatus}</Tag></Descriptions.Item>
          <Descriptions.Item label="可用库存">{p.stock}</Descriptions.Item>
          <Descriptions.Item label="锁定库存">{p.lockedStock}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{p.createTime ? new Date(p.createTime).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="更新时间">{p.updateTime ? new Date(p.updateTime).toLocaleString() : '-'}</Descriptions.Item>
        </Descriptions>
        <Space style={{ marginBottom: 16 }}>
          <span>调整量（正数补货，负数扣减）：</span>
          <InputNumber value={drawerDelta} onChange={v => setDrawerDelta(v)} placeholder="±数量" style={{ width: 150 }} />
          <Button type="primary" onClick={drawerAdjust}>确认调整</Button>
          <Button danger={p.productStatus === 'ENABLED'} onClick={drawerToggle}>{p.productStatus === 'ENABLED' ? '下架' : '上架'}</Button>
        </Space>
        <h4>库存操作日志（最近20条）</h4>
        <Table rowKey="id" dataSource={detail.logs || []} columns={logCols} pagination={false} size="small" />
      </div>}
    </Drawer>
    <Drawer open={pcVisible} width={480} onClose={() => setPcVisible(false)} title="新增商品"
      footer={<div style={{ textAlign: 'right' }}>
        <Button style={{ marginRight: 8 }} onClick={() => setPcVisible(false)}>取消</Button>
        <Button type="primary" onClick={async () => {
          if (!pcForm.title || !pcForm.price || pcForm.price <= 0) { message.error('请填写商品名称和有效价格'); return }
          await stockApi.createProduct({ title: pcForm.title.trim(), price: pcForm.price, stock: pcForm.stock || 0 })
          message.success('商品已创建'); setPcVisible(false); setPcForm({ title: '', price: 100, stock: 100 }); loadProducts()
        }}>创建</Button>
      </div>}>
      <Space direction="vertical" style={{ width: '100%' }} size={16}>
        <div><label style={{ display: 'block', marginBottom: 6 }}>商品名称</label><Input placeholder="请输入商品名称" value={pcForm.title} onChange={e => setPcForm({ ...pcForm, title: e.target.value })} /></div>
        <div><label style={{ display: 'block', marginBottom: 6 }}>价格（分）</label><InputNumber min={1} style={{ width: '100%' }} placeholder="如 1000 表示 ¥10.00" value={pcForm.price} onChange={v => setPcForm({ ...pcForm, price: v || 0 })} /></div>
        <div><label style={{ display: 'block', marginBottom: 6 }}>初始库存</label><InputNumber min={0} style={{ width: '100%' }} value={pcForm.stock} onChange={v => setPcForm({ ...pcForm, stock: v || 0 })} /></div>
      </Space>
    </Drawer>
    </div>
  </div>
}
