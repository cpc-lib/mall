import { useEffect, useState } from 'react'
import { Button, Descriptions, InputNumber, Modal, Radio, Space, Table, Tag, Input, message } from 'antd'
import { QRCodeSVG } from 'qrcode.react'
import checkoutApi from '@/api/checkout'
import refundApi from '@/api/refundApply'
import shipmentApi from '@/api/shipment'
import { availableRefundQuantity, refundAmountEstimate } from '@/utils/refundQuota'
import { SHIPMENT_LABEL, statusTags } from '@/utils/statusLabels'

export default function OrdersV2() {
  const [orders, setOrders] = useState([])
  const [shipments, setShipments] = useState({})
  const [target, setTarget] = useState(null)
  const [refundType, setRefundType] = useState('REFUND_ONLY')
  const [reason, setReason] = useState('用户申请退款')
  const [qty, setQty] = useState({})
  const [logistics, setLogistics] = useState(null)
  const [wxCode, setWxCode] = useState(null)
  const load = async () => {
    const o = await checkoutApi.list()
    const list = o.data || []
    setOrders(list)
    // 已发货订单拉取物流状态，用于「确认收货」按钮可用性
    const shipped = list.filter(d => d.order.fulfillmentStatus === 'SHIPPED')
    const entries = await Promise.all(shipped.map(async d => {
      try { const s = await shipmentApi.getShipment(d.order.orderNo); return [d.order.orderNo, s.data?.status || ''] } catch { return [d.order.orderNo, ''] }
    }))
    setShipments(Object.fromEntries(entries))
  }
  useEffect(() => { load() }, [])
  // 微信支付弹窗打开期间轮询订单状态，支付成功后自动关窗刷新
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
    const items = (target?.items || []).filter(i => Number(qty[i.id] || 0) > 0).map(i => ({ orderItemId: i.id, quantity: Number(qty[i.id]) }))
    if (!items.length) return message.error('至少选择一个退款商品')
    const r = await refundApi.create({ orderNo: target.order.orderNo, refundType, reason: reason.trim(), items })
    message.success(`退款申请已提交，服务端核算金额 ¥${((r.data?.apply?.refundAmount || 0) / 100).toFixed(2)}`)
    setTarget(null); await load()
  }
  const estimateTotal = (target?.items || []).reduce((sum, i) => sum + refundAmountEstimate(i, qty[i.id] || 0), 0)
  const columns = [
    { title: '订单号', render: (_, d) => d.order.orderNo },
    { title: '支付方式', render: (_, d) => d.order.paymentType },
    { title: '金额', render: (_, d) => `¥${((d.order.totalFee || 0) / 100).toFixed(2)}` },
    { title: '状态', render: (_, d) => <Space size={4}>{statusTags(d.order).map(t => <Tag key={t}>{t}</Tag>)}</Space> },
    { title: '商品明细（快照价）', render: (_, d) => d.items.map(i => <div key={i.id}>{i.productTitle} × {i.quantity} @ ¥{((i.unitPrice || 0) / 100).toFixed(2)}；已退 {i.refundedQty || 0}{(i.refundFrozenQty || 0) > 0 ? `，冻结 ${i.refundFrozenQty}` : ''}</div>) },
    {
      title: '操作', width: 300, render: (_, d) => {
        const o = d.order
        const canPay = o.payStatus === 'UNPAID' && o.orderStatus !== '已关闭' && o.fulfillmentStatus !== 'CANCELLED' && (o.orderStatus === '未支付' || o.orderStatus === 'WAIT_PAY')
        const canCancel = o.payStatus === 'PAID' && o.fulfillmentStatus === 'WAIT_SHIP'
        const canConfirm = o.fulfillmentStatus === 'SHIPPED' && shipments[o.orderNo] === 'DELIVERED'
        const canLogistics = ['SHIPPED', 'RECEIVED'].includes(o.fulfillmentStatus)
        const canRefund = o.payStatus === 'PAID' && d.items.some(i => availableRefundQuantity(i) > 0)
        return <Space wrap>
          {canPay && <Button type="primary" onClick={() => pay(d)}>支付</Button>}
          {canCancel && <Button onClick={() => cancelOrder(d)}>取消订单</Button>}
          {canLogistics && <Button onClick={() => showLogistics(d)}>物流详情</Button>}
          {canConfirm && <Button type="primary" ghost onClick={() => confirmReceipt(d)}>确认收货</Button>}
          {canRefund && <Button onClick={() => beginRefund(d)}>分项退款</Button>}
        </Space>
      }
    }
  ]
  const timelineRows = logistics?.timeline ? Object.entries(logistics.timeline).filter(([, v]) => v) : []
  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">我的订单</h2>
      <p className="tb-page-tip">退款金额由服务端按订单快照核算（最后一件吃尾差），页面金额仅为预估。</p>
      <div className="tb-cardbox" style={{ padding: '8px 16px 16px' }}>
        <Table rowKey={d => d.order.orderNo} dataSource={orders} columns={columns} />
      </div>
    </div>
    <Modal open={!!target} title="分项退款" onCancel={() => setTarget(null)} onOk={submitRefund} okButtonProps={{ disabled: estimateTotal <= 0 }}>
      {target && <div style={{ marginBottom: 12 }}>
        <Radio.Group value={refundType} onChange={e => setRefundType(e.target.value)}>
          {target.order.fulfillmentStatus === 'WAIT_SHIP' && <Radio value="CANCEL_BEFORE_SHIP">未发货取消（补库存）</Radio>}
          {target.order.fulfillmentStatus === 'SHIPPED' && <Radio value="REFUND_ONLY">仅退款（未收货）</Radio>}
          {target.order.fulfillmentStatus === 'RECEIVED' && <Radio value="REFUND_ONLY">仅退款</Radio>}
          {target.order.fulfillmentStatus === 'RECEIVED' && <Radio value="RETURN_AND_REFUND">退货退款（签收质检后补库存）</Radio>}
        </Radio.Group>
      </div>}
      {target?.items.map(i => {
        const max = availableRefundQuantity(i)
        return <div key={i.id} style={{ marginBottom: 12 }}><b>{i.productTitle}</b>：<InputNumber min={0} max={max} value={qty[i.id] || 0} disabled={max <= 0} onChange={v => setQty({ ...qty, [i.id]: Math.min(max, Number(v || 0)) })} /> / 可退 {max} 件，快照价 ¥{((i.unitPrice || 0) / 100).toFixed(2)}</div>
      })}
      <Input value={reason} onChange={e => setReason(e.target.value)} placeholder="退款原因" maxLength={255} />
      <div style={{ marginTop: 12 }}>预估退款：<b>¥{(estimateTotal / 100).toFixed(2)}</b>（以服务端核算为准）</div>
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
        <p style={{ color: '#999', fontSize: 12, marginBottom: 0 }}>支付成功后页面自动刷新，无需手动关闭</p>
        <div style={{ marginTop: 8, wordBreak: 'break-all', color: '#bbb', fontSize: 11 }}>{wxCode?.codeUrl}</div>
      </div>
    </Modal>
  </div>
}
