import { useEffect, useMemo, useState } from 'react'
import { Button, Cascader, Empty, Form, Input, Modal, message } from 'antd'
import { Link } from 'react-router-dom'
import cartApi from '@/api/cart'
import checkoutApi from '@/api/checkout'
import paymentConfigApi from '@/api/paymentConfig'
import addressApi from '@/api/address'
import request from '@/utils/request'
import { mallImg, onMallImgError } from '@/assets/mallImgs'

const paymentTypeOf = code => code === 'WXPAY' ? '微信' : code === 'ALIPAY' ? '支付宝' : ''
const CHANNEL_META = {
  WXPAY: { name: '微信支付', desc: '使用微信扫码支付（沙箱演示）', glyph: '微', cls: 'wx' },
  ALIPAY: { name: '支付宝', desc: '跳转支付宝完成支付（沙箱演示）', glyph: '支', cls: 'ali' }
}
const channelMetaOf = code => CHANNEL_META[code] || { name: code, desc: '沙箱演示渠道', glyph: (code || '?').slice(0, 1), cls: '' }

export default function Cart() {
  const [items, setItems] = useState([])
  const [apps, setApps] = useState([])
  const [channelCode, setChannelCode] = useState('')
  const [addressList, setAddressList] = useState([])
  const [selectedAddrId, setSelectedAddrId] = useState(null)
  const [addrOpen, setAddrOpen] = useState(false)
  const [addrForm] = Form.useForm()
  const [regions, setRegions] = useState([])
  const load = async () => {
    const [cart, payApps, addrs] = await Promise.all([cartApi.list(), paymentConfigApi.listEnabledApps(), addressApi.list()])
    setItems(cart.data || [])
    setApps(payApps.data || [])
    const list = addrs.data || []
    setAddressList(list)
    if (list.length > 0) {
      const def = list.find(a => a.isDefault === '1') || list[0]
      setSelectedAddrId(def.id)
    } else {
      setSelectedAddrId(null)
    }
  }
  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { request.get('/api/regions/tree').then(r => setRegions(r.data || [])).catch(() => {}) }, [])

  const channels = useMemo(() => {
    const seen = new Set(); const list = []
    for (const a of apps) {
      if (!a.channelCode || seen.has(a.channelCode)) continue
      seen.add(a.channelCode)
      list.push({ code: a.channelCode, appId: a.id, ...channelMetaOf(a.channelCode) })
    }
    return list
  }, [apps])
  useEffect(() => {
    if (!channels.some(c => c.code === channelCode)) setChannelCode(channels[0]?.code || '')
  }, [channels]) // eslint-disable-line react-hooks/exhaustive-deps

  const selectedItems = items.filter(i => i.selected)
  const total = selectedItems.reduce((s, i) => s + Number(i.latestPrice || 0) * Number(i.quantity || 0), 0)
  const canCheckout = selectedItems.length > 0 && selectedItems.every(i => i.available) && Boolean(channelCode)
  const changeQty = async (row, q) => { await cartApi.put({ productId: row.productId, quantity: q, selected: row.selected }); await load() }

  const checkout = async () => {
    const app = apps.find(a => a.channelCode === channelCode)
    if (!app) return message.error('请选择支付方式')
    const paymentType = paymentTypeOf(channelCode)
    if (!paymentType) return message.error(`暂不支持渠道 ${channelCode}`)
    const payload = { paymentType, paymentAppId: app.id }
    if (selectedAddrId) payload.addressId = selectedAddrId
    const r = await checkoutApi.create(payload)
    message.success(`订单已创建：${r.data?.order?.orderNo}`)
    location.hash = '#/orders'
  }

  const submitAddr = async () => {
    const v = await addrForm.validateFields()
    const [province, city, district] = v.region || []
    const payload = {
      receiverName: v.receiverName, receiverPhone: v.receiverPhone, detail: v.detail,
      province: regions.find(p => p.value === province)?.label || province || '',
      city: regions.find(p => p.value === province)?.children?.find(c => c.value === city)?.label || city || '',
      district: regions.find(p => p.value === province)?.children?.find(c => c.value === city)?.children?.find(d => d.value === district)?.label || district || '',
      isDefault: v.isDefault ? '1' : '0'
    }
    const r = await addressApi.create(payload)
    message.success('已新增'); setAddrOpen(false); setAddrSelected(r.data.id); await load()
  }
  const setAddrSelected = (id) => setSelectedAddrId(id)

  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">我的购物车</h2>
      {items.length === 0
        ? <div className="tb-cardbox"><Empty description="购物车还是空的">
          <Link to="/"><Button type="primary">去逛逛</Button></Link>
        </Empty></div>
        : items.map(r => (
        <div className="tb-cart-card" key={r.productId}>
          <label className="tb-cart-check">
            <input type="checkbox" checked={r.selected} onChange={async e => { await cartApi.select(r.productId, e.target.checked); await load() }} />
          </label>
          <div className="tb-cart-thumb" style={{ aspectRatio: '1/1' }}>
            <img src={mallImg(r.productId)} alt={r.title} onError={onMallImgError} />
          </div>
          <div className="tb-cart-info">
            <div className="tb-goods-title">{r.title}</div>
            <div className="tb-cart-sub">{r.available ? `可售 · 库存 ${r.availableStock}` : '库存不足'}</div>
            <div className="tb-cart-meta">
              <span className="tb-cart-price">¥{((r.latestPrice || 0) / 100).toFixed(2)}</span>
              <span className="tb-cart-del" onClick={async () => { await cartApi.remove(r.productId); await load() }}>删除</span>
              <div className="tb-cart-qty">
                <button type="button" disabled={r.quantity <= 1} onClick={() => changeQty(r, r.quantity - 1)}>-</button>
                <span>{r.quantity}</span>
                <button type="button" disabled={!r.available || r.quantity >= (r.availableStock || 99)} onClick={() => changeQty(r, r.quantity + 1)}>+</button>
              </div>
            </div>
          </div>
        </div>
      ))}
      {items.length > 0 && <div className="tb-receiver">
        <div className="tb-addr-row">
          <div className="tb-addr-label">收货地址</div>
          {addressList.length === 0 ? (
            <div className="tb-addr-empty">暂无地址
              <Button size="small" type="link" onClick={() => { addrForm.resetFields(); addrForm.setFieldsValue({ isDefault: true }); setAddrOpen(true) }}>+ 立即新增</Button>
              <Link to="/addresses" className="tb-addr-link" style={{ fontSize: 12, marginLeft: 8 }}>地址管理</Link>
            </div>
          ) : (
            <div className="tb-addr-list">
              {addressList.map(a => (
                <div key={a.id} className={`tb-addr-item${selectedAddrId === a.id ? ' active' : ''}`} onClick={() => setAddrSelected(a.id)}>
                  <div className="tb-addr-head">
                    <b>{a.receiverName}</b> <span>{a.receiverPhone}</span>
                    {a.isDefault === '1' && <span className="tb-addr-tag">默认</span>}
                  </div>
                  <div className="tb-addr-body">{a.province}{a.city}{a.district}{a.detail}</div>
                </div>
              ))}
              <Button size="small" type="dashed" style={{ height: 56, width: 110 }} onClick={() => { addrForm.resetFields(); addrForm.setFieldsValue({ isDefault: addressList.length === 0 }); setAddrOpen(true) }}>+ 新增</Button>
              <Link to="/addresses" className="tb-addr-link">管理 ›</Link>
            </div>
          )}
        </div>
        <div className="m-pay-label">支付方式</div>
        <div className="m-pay-list">
          {channels.map(c => (
            <div key={c.code} className={`m-pay-item${c.code === channelCode ? ' active' : ''}`} onClick={() => setChannelCode(c.code)}>
              <div className={`m-pay-logo ${c.cls}`}>{c.glyph}</div>
              <div className="m-pay-info">
                <div className="m-pay-name">{c.name}</div>
                <div className="m-pay-desc">{c.desc}</div>
              </div>
              <div className="m-pay-check" />
            </div>
          ))}
        </div>
      </div>}
      <div className="tb-cart-bar">
        <span className="m-dim">已选 <b className="m-count">{selectedItems.length}</b> 件商品</span>
        <div className="tb-cart-total">合计：<b>¥{(total / 100).toFixed(2)}</b></div>
        <Button className="tb-cart-checkout" disabled={!canCheckout} onClick={checkout}>结 算</Button>
      </div>
    </div>
    <Modal open={addrOpen} onCancel={() => setAddrOpen(false)} title="新增收货地址" onOk={submitAddr}>
      <Form form={addrForm} layout="vertical" preserve={false}>
        <Form.Item name="receiverName" label="收货人" rules={[{ required: true, message: '请输入收货人姓名' }]}><Input maxLength={32} /></Form.Item>
        <Form.Item name="receiverPhone" label="电话" rules={[{ required: true, message: '请输入手机号' }, { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' }]}><Input maxLength={32} /></Form.Item>
        <Form.Item name="region" label="所在地区" rules={[{ required: true, message: '请选择省/市/区' }]}><Cascader options={regions} placeholder="省 / 市 / 区" changeOnSelect /></Form.Item>
        <Form.Item name="detail" label="详细地址" rules={[{ required: true, message: '请输入详细地址' }]}><Input.TextArea maxLength={128} rows={2} placeholder="街道、门牌号等" /></Form.Item>
      </Form>
    </Modal>
  </div>
}
