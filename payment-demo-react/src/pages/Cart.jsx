import { useEffect, useMemo, useState } from 'react'
import { Button, Checkbox, Empty, Input, InputNumber, message } from 'antd'
import cartApi from '@/api/cart'
import checkoutApi from '@/api/checkout'
import paymentConfigApi from '@/api/paymentConfig'
import { mallImg, onMallImgError } from '@/assets/mallImgs'

const paymentTypeOf = code => code === 'WXPAY' ? '微信' : code === 'ALIPAY' ? '支付宝' : ''
const CHANNEL_META = {
  WXPAY: { name: '微信支付', desc: '使用微信扫码支付（沙箱演示）', glyph: '微', cls: 'wx' },
  ALIPAY: { name: '支付宝', desc: '跳转支付宝完成支付（沙箱演示）', glyph: '支', cls: 'ali' }
}
const channelMetaOf = code => CHANNEL_META[code] || { name: code, desc: '沙箱演示渠道', glyph: (code || '?').slice(0, 1), cls: '' }

// 精致现代风购物车：商品行 + 收货信息 + 支付渠道选择 + 吸底结算条
export default function Cart() {
  const [items, setItems] = useState([])
  const [apps, setApps] = useState([])
  const [channelCode, setChannelCode] = useState('')
  const [receiver, setReceiver] = useState({ name: '', phone: '', address: '' })
  const load = async () => {
    const [cart, payApps] = await Promise.all([cartApi.list(), paymentConfigApi.listEnabledApps()])
    setItems(cart.data || [])
    setApps(payApps.data || [])
  }
  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps
  // 渠道级去重：同渠道多应用时默认取第一个启用应用，用户视角只感知「支付方式」
  const channels = useMemo(() => {
    const seen = new Set()
    const list = []
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
    const r = await checkoutApi.create({
      paymentType, paymentAppId: app.id,
      receiverName: receiver.name.trim() || undefined,
      receiverPhone: receiver.phone.trim() || undefined,
      receiverAddress: receiver.address.trim() || undefined
    })
    message.success(`订单已创建：${r.data?.order?.orderNo}`)
    location.hash = '#/orders'
  }
  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">我的购物车</h2>
      <div className="tb-cart-head">
        <div className="tb-col-check">勾选</div>
        <div className="tb-col-goods">商品信息</div>
        <div className="tb-col-price">单价</div>
        <div className="tb-col-qty">数量</div>
        <div className="tb-col-sum">小计</div>
        <div className="tb-col-op">操作</div>
      </div>
      {items.length === 0
        ? <div className="tb-cardbox"><Empty description="购物车还是空的，去挑点好物吧" /></div>
        : items.map(r => (
        <div className="tb-cart-row" key={r.productId}>
          <div className="tb-col-check"><Checkbox checked={r.selected} onChange={async v => { await cartApi.select(r.productId, v.target.checked); await load() }} /></div>
          <div className="tb-col-goods">
            <img src={mallImg(r.productId)} alt={r.title} onError={onMallImgError} />
            <div style={{ minWidth: 0 }}>
              <div className="tb-goods-title">{r.title}</div>
              <div className="tb-goods-sub">{r.available ? `可售 · 可用库存 ${r.availableStock}` : '不可售 / 库存不足'}</div>
            </div>
          </div>
          <div className="tb-col-price">¥{((r.latestPrice || 0) / 100).toFixed(2)}</div>
          <div className="tb-col-qty"><InputNumber size="small" min={1} max={Math.max(1, r.availableStock || 1)} value={r.quantity} onChange={v => changeQty(r, v || 1)} /></div>
          <div className="tb-col-sum">¥{((Number(r.latestPrice || 0) * Number(r.quantity || 0)) / 100).toFixed(2)}</div>
          <div className="tb-col-op"><a onClick={async () => { await cartApi.remove(r.productId); await load() }}>删除</a></div>
        </div>
      ))}
      {items.length > 0 && <div className="tb-receiver">
        <Input style={{ width: 140 }} placeholder="收货人姓名（选填）" value={receiver.name} onChange={e => setReceiver({ ...receiver, name: e.target.value })} />
        <Input style={{ width: 160 }} placeholder="收货人电话（选填）" value={receiver.phone} onChange={e => setReceiver({ ...receiver, phone: e.target.value })} />
        <Input style={{ width: 320 }} placeholder="收货地址（选填，缺省模拟值）" value={receiver.address} onChange={e => setReceiver({ ...receiver, address: e.target.value })} />
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
  </div>
}
