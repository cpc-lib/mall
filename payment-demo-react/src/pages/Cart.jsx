import { useEffect, useMemo, useState } from 'react'
import { Button, Checkbox, Input, InputNumber, Select, Space, message } from 'antd'
import cartApi from '@/api/cart'
import checkoutApi from '@/api/checkout'
import paymentConfigApi from '@/api/paymentConfig'
import { mallImg, onMallImgError } from '@/assets/mallImgs'

const paymentTypeOf = code => code === 'WXPAY' ? '微信' : code === 'ALIPAY' ? '支付宝' : ''

// 淘宝风格购物车：商品行 + 收货信息 + 吸底结算条
export default function Cart() {
  const [items, setItems] = useState([])
  const [apps, setApps] = useState([])
  const [paymentAppId, setPaymentAppId] = useState()
  const [receiver, setReceiver] = useState({ name: '', phone: '', address: '' })
  const load = async () => {
    const [cart, payApps] = await Promise.all([cartApi.list(), paymentConfigApi.listEnabledApps()])
    setItems(cart.data || [])
    const nextApps = payApps.data || []
    setApps(nextApps)
    if (!nextApps.some(a => a.id === paymentAppId)) setPaymentAppId(nextApps[0]?.id)
  }
  useEffect(() => { load() }, []) // eslint-disable-line react-hooks/exhaustive-deps
  const selectedApp = useMemo(() => apps.find(a => a.id === paymentAppId), [apps, paymentAppId])
  const selectedItems = items.filter(i => i.selected)
  const total = selectedItems.reduce((s, i) => s + Number(i.latestPrice || 0) * Number(i.quantity || 0), 0)
  const canCheckout = selectedItems.length > 0 && selectedItems.every(i => i.available) && Boolean(selectedApp)
  const changeQty = async (row, q) => { await cartApi.put({ productId: row.productId, quantity: q, selected: row.selected }); await load() }
  const checkout = async () => {
    if (!selectedApp) return message.error('请选择支付应用')
    const paymentType = paymentTypeOf(selectedApp.channelCode)
    if (!paymentType) return message.error(`暂不支持渠道 ${selectedApp.channelCode}`)
    const r = await checkoutApi.create({
      paymentType, paymentAppId: selectedApp.id,
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
        ? <div className="tb-cardbox" style={{ borderRadius: '0 0 8px 8px' }}><Empty description="购物车还是空的，去挑点好物吧" /></div>
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
        <Space>支付应用：<Select style={{ minWidth: 240 }} value={paymentAppId} placeholder="选择支付应用" onChange={setPaymentAppId} options={apps.map(a => ({ value: a.id, label: `${a.appName} / ${a.channelName || a.channelCode}` }))} /></Space>
      </div>}
      <div className="tb-cart-bar">
        <span style={{ fontSize: 13, color: '#6C6C6C' }}>已选 <b style={{ color: '#FF5000' }}>{selectedItems.length}</b> 件商品</span>
        <div className="tb-cart-total">合计：<b>¥{(total / 100).toFixed(2)}</b></div>
        <Button className="tb-cart-checkout" style={{}} disabled={!canCheckout} onClick={checkout}>结 算</Button>
      </div>
    </div>
  </div>
}
