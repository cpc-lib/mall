import { useEffect, useMemo, useState } from 'react'
import { Button, Empty, Input, InputNumber, message, Modal, Tag } from 'antd'
import refundApi from '@/api/refundApply'
import checkoutApi from '@/api/checkout'
import { availableRefundQuantityExcluding, refundAmountEstimate } from '@/utils/refundQuota'
import { REFUND_STATUS_COLOR, REFUND_STATUS_LABEL, REFUND_TYPE_LABEL } from '@/utils/statusLabels'

export default function RefundApplications() {
  const [list, setList] = useState([])
  const [orders, setOrders] = useState([])
  const [editing, setEditing] = useState(null)
  const [reason, setReason] = useState('')
  const [quantities, setQuantities] = useState({})
  const load = async () => { const [r, o] = await Promise.all([refundApi.mine(), checkoutApi.list()]); setList(r.data || []); setOrders(o.data || []) }
  useEffect(() => { load() }, [])
  const orderItemMap = useMemo(() => { const m = new Map(); orders.forEach(o => o.items?.forEach(i => m.set(Number(i.id), i))); return m }, [orders])
  const beginEdit = row => { setEditing(row); setReason(row.apply.reason || ''); const next = {}; row.items.forEach(i => { next[i.orderItemId] = i.refundQty }); setQuantities(next) }
  const maxFor = item => {
    const orderItem = orderItemMap.get(Number(item.orderItemId))
    if (!orderItem) return Number(item.refundQty || 0)
    return availableRefundQuantityExcluding(orderItem, item.refundQty)
  }
  const save = async () => {
    const items = editing.items.map(i => ({ orderItemId: i.orderItemId, quantity: Number(quantities[i.orderItemId] || 0) })).filter(i => i.quantity > 0)
    if (!items.length) return message.error('至少保留一个退款商品')
    await refundApi.update(editing.apply.refundNo, { reason: reason.trim(), items })
    message.success('退款申请已修改'); setEditing(null); await load()
  }
  const cancel = async refundNo => { await refundApi.cancel(refundNo); message.success('退款申请已撤销'); await load() }
  const editTotal = editing?.items.reduce((sum, i) => {
    const orderItem = orderItemMap.get(Number(i.orderItemId))
    return sum + (orderItem ? refundAmountEstimate(orderItem, quantities[i.orderItemId] || 0) : 0)
  }, 0) || 0
  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">我的退款申请</h2>
      <p className="tb-page-tip">仅待审核（APPLYING）可编辑/撤销；受理后冻结额度并走审核/渠道退款链路。金额以服务端核算为准。</p>
      {list.length === 0
        ? <div className="tb-cardbox"><Empty description="暂无退款申请" /></div>
        : list.map(r => (
        <div className="m-list-card" key={r.apply.refundNo}>
          <div className="m-list-head">
            <div className="m-list-no">退款号 {r.apply.refundNo}<br />订单号 {r.apply.orderNo}</div>
            <div className="m-list-tags">
              <Tag color={REFUND_STATUS_COLOR[r.apply.status] || 'blue'}>{REFUND_STATUS_LABEL[r.apply.status] || r.apply.status}</Tag>
            </div>
          </div>
          <div className="m-list-sub">
            <span className="m-list-sub-label">{REFUND_TYPE_LABEL[r.apply.refundType] || r.apply.refundType}</span>
            <span className="m-list-amount">¥{((r.apply.refundAmount || 0) / 100).toFixed(2)}</span>
          </div>
          <div className="m-list-items">
            {r.items.map(i => <div key={i.id}>item#{i.orderItemId} <span className="m-list-item-sub">× {i.refundQty} @ ¥{((i.unitPrice || 0) / 100).toFixed(2)}</span></div>)}
            <div>原因：{r.apply.reason}</div>
            {r.apply.status === 'FAILED' && <div className="m-list-fail">失败原因：{r.failReason || '渠道退款失败，请联系管理员重试'}</div>}
          </div>
          <div className="m-list-actions">
            {r.apply.status === 'APPLYING'
              ? <><Button onClick={() => beginEdit(r)}>编辑</Button><Button danger onClick={() => cancel(r.apply.refundNo)}>撤销</Button></>
              : <span className="m-list-item-sub">已受理，不可编辑/撤销</span>}
          </div>
        </div>
      ))}
    </div>
    <Modal open={!!editing} title="编辑待审核退款申请" onCancel={() => setEditing(null)} onOk={save} okButtonProps={{ disabled: editTotal <= 0 }}>
      {editing?.items.map(i => { const max = maxFor(i); return <div key={i.id} className="m-modal-line"><b>item#{i.orderItemId}</b><InputNumber min={0} max={max} value={quantities[i.orderItemId] || 0} onChange={v => setQuantities({ ...quantities, [i.orderItemId]: Math.min(max, Number(v || 0)) })} /><span className="m-modal-hint">最大 {max} 件</span></div> })}
      <Input value={reason} onChange={e => setReason(e.target.value)} placeholder="退款原因" maxLength={255} /><div style={{ marginTop: 12 }}>修改后预估金额：¥{(editTotal / 100).toFixed(2)}</div>
    </Modal>
  </div>
}
