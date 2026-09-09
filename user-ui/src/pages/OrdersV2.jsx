import { useEffect, useState } from 'react'
import { Alert, Button, Descriptions, Empty, InputNumber, Modal, Radio, Tag, Input, message } from 'antd'
import { QRCodeSVG } from 'qrcode.react'
import checkoutApi from '@/api/checkout'
import refundApi from '@/api/refundApply'
import shipmentApi from '@/api/shipment'
import { availableRefundQuantity, refundAmountEstimate } from '@/utils/refundQuota'
import { SHIPMENT_LABEL, statusTags } from '@/utils/statusLabels'

export default function OrdersV2() {
  const [orders, setOrders] = useState([])
  const [target, setTarget] = useState(null)
  const [refundType, setRefundType] = useState('REFUND_ONLY')
  const [reason, setReason] = useState('用户申请退款')
  const [qty, setQty] = useState({})
  const [logistics, setLogistics] = useState(null)
  const [wxCode, setWxCode] = useState(null)
  const [payQuerying, setPayQuerying] = useState(false)
  const load = async () => {
    const o = await checkoutApi.list()
    setOrders(o.data || [])
  }
  useEffect(() => { load() }, [])
  useEffect(() => {
    const hasShipped = orders.some(d => d.order.fulfillmentStatus === 'SHIPPED')
    if (!hasShipped) return
    const timer = setInterval(load, 15000)
    return () => clearInterval(timer)
  }, [orders])
  useEffect(() => {
    if (!wxCode) return
    const timer = setInterval(async () => {
      try {
        const o = await checkoutApi.list()
        const hit = (o.data || []).find(x => x.order.orderNo === wxCode.orderNo)
        if (hit?.order?.payStatus === 'PAID') {
          clearInterval(timer)
          setWxCode(null)
          message.success('支付成功')
          await load()
        }
      } catch { /* 轮询失败忽略，下个周期重试 */ }
    }, 3000)
    return () => clearInterval(timer)
  }, [wxCode])
  const pay = async d => {
    if (d.order.paymentType === '支付宝') {
      const r = await checkoutApi.alipay(d.order.orderNo)
      const w = window.open('', '_blank'); if (w) { w.document.open(); w.document.write(r.data?.html || ''); w.document.close() }
    } else {
      const r = await checkoutApi.wxpay(d.order.orderNo)
      const codeUrl = r.data?.codeUrl || r.data?.code_url
      if (!codeUrl) { message.error('未获取到微信支付二维码，请稍后重试'); return }
      setWxCode({ orderNo: d.order.orderNo, codeUrl })
    }
  }
  const queryPayResult = async () => {
    if (!wxCode) return
    setPayQuerying(true)
    try {
      const r = await checkoutApi.payQuery(wxCode.orderNo)
      const desc = r.data?.channelTradeStateDesc
      if (r.data?.payStatus === 'PAID') {
        setWxCode(null)
        message.success('支付成功')
        await load()
      } else {
        message.info(`渠道暂未确认支付${desc ? `（${desc}）` : ''}，若已完成支付请稍候几秒再点查询`)
      }
    } finally { setPayQuerying(false) }
  }
  const cancelOrder = async d => {
    Modal.confirm({
      title: '取消订单', content: '已付款未发货订单将创建「未发货取消」退款申请，受理后自动原路退回，库存自动回补。',
      onOk: async () => {
        const r = await shipmentApi.cancelPaidOrder(d.order.orderNo)
        message.success(`取消申请已提交，预估退款 ¥${((r.data?.apply?.refundAmount || 0) / 100).toFixed(2)}`)
        await load()
      }
    })
  }
  const showLogistics = async d => {
    const r = await shipmentApi.getShipment(d.order.orderNo)
    setLogistics(r.data || { shipped: false })
  }
  const confirmReceipt = async d => {
    await shipmentApi.confirmReceipt(d.order.orderNo)
    message.success('确认收货成功，交易完成'); await load()
  }
  const beginRefund = d => {
    const next = {}
    d.items.forEach(i => { next[i.id] = 0 })
    setQty(next); setReason('用户申请退款')
    setRefundType(d.order.fulfillmentStatus === 'RECEIVED' ? 'REFUND_ONLY' : d.order.fulfillmentStatus === 'SHIPPED' ? 'REFUND_ONLY' : 'CANCEL_BEFORE_SHIP')
    setTarget(d)
  }
  const submitRefund = async () => {
    if (refundType === 'REFUND_ONLY') {
      const r = await refundApi.create({ orderNo: target.order.orderNo, refundType, reason: reason.trim(), items: [] })
      const amountY = `¥${((r.data?.apply?.refundAmount || 0) / 100).toFixed(2)}`
      message.success(`仅退款申请已提交，整单全额 ${amountY}，商品不退回（计入货损）`)
      setTarget(null); await load(); return
    }
    const items = (target?.items || []).filter(i => Number(qty[i.id] || 0) > 0).map(i => ({ orderItemId: i.id, quantity: Number(qty[i.id]) }))
    if (!items.length) return message.error('至少选择一个退款商品')
    const r = await refundApi.create({ orderNo: target.order.orderNo, refundType, reason: reason.trim(), items })
    const amountY = `¥${((r.data?.apply?.refundAmount || 0) / 100).toFixed(2)}`
    if (refundType === 'CANCEL_BEFORE_SHIP') {
      message.success(`未发货取消已受理，退款 ${amountY} 处理中，库存已自动回补`)
    } else {
      message.success(`退款申请已提交，服务端核算金额 ${amountY}`)
    }
    setTarget(null); await load()
  }
  const estimateTotal = (target?.items || []).reduce((sum, i) => sum + refundAmountEstimate(i, qty[i.id] || 0), 0)
  const fullRefundTotal = (target?.items || []).reduce((sum, i) => sum + refundAmountEstimate(i, availableRefundQuantity(i)), 0)
  const isRefundOnly = refundType === 'REFUND_ONLY'
  const timelineRows = logistics?.timeline ? Object.entries(logistics.timeline).filter(([, v]) => v) : []
  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">我的订单</h2>
      {orders.length === 0
        ? <div className="tb-cardbox"><Empty description="暂无订单，去首页挑点好物吧" /></div>
        : orders.map(d => {
          const o = d.order
          const noRefund = o.refundStatus == null || o.refundStatus === 'NONE'
          const canPay = o.payStatus === 'UNPAID' && o.orderStatus !== '已关闭' && o.fulfillmentStatus !== 'CANCELLED' && (o.orderStatus === '未支付' || o.orderStatus === 'WAIT_PAY')
          const canCancel = o.payStatus === 'PAID' && o.fulfillmentStatus === 'WAIT_SHIP' && noRefund
          const canConfirm = o.fulfillmentStatus === 'SHIPPED' && d.shipmentStatus === 'DELIVERED' && noRefund
          const canLogistics = ['SHIPPED', 'RECEIVED'].includes(o.fulfillmentStatus) && noRefund
          // 未发货已付款只走「取消订单」（整单取消、补库存），不显示「申请退款」；发货/收货后才走仅退款/退货退款
          const canRefund = o.payStatus === 'PAID' && noRefund && o.fulfillmentStatus !== 'WAIT_SHIP' && d.items.some(i => availableRefundQuantity(i) > 0)
          return <div className="m-list-card" key={o.orderNo}>
            <div className="m-list-head">
              <div className="m-list-no">订单号 {o.orderNo}</div>
              <div className="m-list-tags">{[...statusTags(o), (d.shipmentStatus === 'IN_TRANSIT' || d.shipmentStatus === 'DELIVERED') ? SHIPMENT_LABEL[d.shipmentStatus] : null].filter(Boolean).map(t => <Tag key={t}>{t}</Tag>)}</div>
            </div>
            <div className="m-list-sub">
              <span className="m-list-sub-label">{o.paymentType}</span>
              <span className="m-list-amount">¥{((o.totalFee || 0) / 100).toFixed(2)}</span>
            </div>
            <div className="m-list-items">
              {d.items.map(i => <div key={i.id}>{i.productTitle} <span className="m-list-item-sub">× {i.quantity} @ ¥{((i.unitPrice || 0) / 100).toFixed(2)}；已退 {i.refundedQty || 0}{(i.refundFrozenQty || 0) > 0 ? `，冻结 ${i.refundFrozenQty}` : ''}</span></div>)}
            </div>
            <div className="m-list-actions">
              {canPay && <Button type="primary" onClick={() => pay(d)}>支付</Button>}
              {canCancel && <Button onClick={() => cancelOrder(d)}>取消订单</Button>}
              {canLogistics && <Button onClick={() => showLogistics(d)}>物流详情</Button>}
              {canConfirm && <Button type="primary" onClick={() => confirmReceipt(d)}>确认收货</Button>}
              {canRefund && <Button onClick={() => beginRefund(d)}>申请退款</Button>}
            </div>
          </div>
        })}
    </div>
    <Modal open={!!target} title={isRefundOnly ? '申请仅退款（整单全额）' : '分项退款'} onCancel={() => setTarget(null)} onOk={submitRefund} okButtonProps={{ disabled: isRefundOnly ? fullRefundTotal <= 0 : estimateTotal <= 0 }}>
      {target && <div style={{ marginBottom: 12 }}>
        <Radio.Group value={refundType} onChange={e => setRefundType(e.target.value)}>
          {target.order.fulfillmentStatus === 'WAIT_SHIP' && <Radio value="CANCEL_BEFORE_SHIP">未发货取消（补库存）</Radio>}
          {target.order.fulfillmentStatus === 'SHIPPED' && <Radio value="REFUND_ONLY">仅退款（未收货）</Radio>}
          {target.order.fulfillmentStatus === 'RECEIVED' && <Radio value="REFUND_ONLY">仅退款</Radio>}
          {target.order.fulfillmentStatus === 'RECEIVED' && <Radio value="RETURN_AND_REFUND">退货退款（签收质检后补库存）</Radio>}
        </Radio.Group>
      </div>}
      {isRefundOnly
        ? <Alert type="warning" showIcon style={{ marginBottom: 12 }} message="整单全额退款，商品无需退回，计入商家货损（丢失库存）。" />
        : target?.items.map(i => {
          const max = availableRefundQuantity(i)
          return <div key={i.id} className="m-modal-line"><b>{i.productTitle}</b><InputNumber min={0} max={max} value={qty[i.id] || 0} disabled={max <= 0} onChange={v => setQty({ ...qty, [i.id]: Math.min(max, Number(v || 0)) })} /><span className="m-modal-hint">可退 {max} 件，快照价 ¥{((i.unitPrice || 0) / 100).toFixed(2)}</span></div>
        })}
      <Input value={reason} onChange={e => setReason(e.target.value)} placeholder="退款原因" maxLength={255} />
      <div style={{ marginTop: 12 }}>预估退款：<b>¥{((isRefundOnly ? fullRefundTotal : estimateTotal) / 100).toFixed(2)}</b>（以服务端核算为准）</div>
    </Modal>
    <Modal open={!!logistics} title="物流详情" footer={null} onCancel={() => setLogistics(null)}>
      {logistics?.shipped
        ? <Descriptions column={1} size="small" bordered>
          <Descriptions.Item label="快递公司">{logistics.logisticsCompany}</Descriptions.Item>
          <Descriptions.Item label="运单号">{logistics.trackingNo}</Descriptions.Item>
          <Descriptions.Item label="物流状态">{SHIPMENT_LABEL[logistics.status] || logistics.status}</Descriptions.Item>
          {timelineRows.map(([k, v]) => <Descriptions.Item key={k} label={SHIPMENT_LABEL[k] || k}>{new Date(v).toLocaleString()}</Descriptions.Item>)}
        </Descriptions>
        : <p>订单尚未发货</p>}
    </Modal>
    <Modal open={!!wxCode} title="微信扫码支付" footer={null} width={380} centered onCancel={() => setWxCode(null)}>
      <div style={{ textAlign: 'center' }}>
        {wxCode?.codeUrl && <div className="qr-frame"><QRCodeSVG value={wxCode.codeUrl} size={280} level="M" includeMargin /></div>}
        <p style={{ marginTop: 14, marginBottom: 4, fontWeight: 600 }}>请使用微信扫描二维码完成支付</p>
        <p style={{ color: '#999', fontSize: 12, marginBottom: 12 }}>支付完成后点击下方按钮查询结果，页面也会自动刷新</p>
        <Button type="primary" block loading={payQuerying} onClick={queryPayResult}>我已支付，查询支付结果</Button>
        <div style={{ marginTop: 10, wordBreak: 'break-all', color: '#bbb', fontSize: 11 }}>{wxCode?.codeUrl}</div>
      </div>
    </Modal>
  </div>
}
