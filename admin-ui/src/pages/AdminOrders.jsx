import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Descriptions, Drawer, Input, InputNumber, message, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import shipmentApi from '@/api/shipment'
import refundApi from '@/api/refundApply'
import { PAY_LABEL, FULFILLMENT_LABEL } from '@/utils/statusLabels'

export default function AdminOrders() {
  const [allOrders, setAllOrders] = useState([])
  const [orderFilter, setOrderFilter] = useState({ payStatus: '', orderStatus: '', fulfillmentStatus: '' })
  const [orderNo, setOrderNo] = useState('')
  const [userId, setUserId] = useState('')
  const [dateRange, setDateRange] = useState(null)
  const [detailRow, setDetailRow] = useState(null)
  const [paAmount, setPaAmount] = useState(0)
  const [paReason, setPaReason] = useState('差价补偿')
  const [channelQueryRow, setChannelQueryRow] = useState(null)
  const [channelQueryLoading, setChannelQueryLoading] = useState(false)
  const [payAttempts, setPayAttempts] = useState([])
  const loadAllOrders = () => {
    const params = {}
    if (orderFilter.payStatus) params.payStatus = orderFilter.payStatus
    if (orderFilter.orderStatus) params.orderStatus = orderFilter.orderStatus
    if (orderFilter.fulfillmentStatus) params.fulfillmentStatus = orderFilter.fulfillmentStatus
    if (orderNo.trim()) params.orderNo = orderNo.trim()
    if (userId.trim()) params.userId = userId.trim()
    if (dateRange && dateRange.length === 2) {
      params.startTime = dateRange[0].format('YYYY-MM-DD')
      params.endTime = dateRange[1].format('YYYY-MM-DD')
    }
    shipmentApi.allOrders(params).then(r => {
      const list = r.data || []
      setAllOrders(list)
      setDetailRow(prev => {
        if (!prev) return null
        return list.find(x => x.order.orderNo === prev.order.orderNo) || prev
      })
    }).catch(() => setAllOrders([]))
  }
  useEffect(loadAllOrders, [])
  const forceClose = async orderNo => { Modal.confirm({ title: '强制关单', content: '将关闭未支付订单并释放预占库存，确认操作？', onOk: async () => { await shipmentApi.forceClose(orderNo); message.success('订单已强制关闭'); loadAllOrders() } }) }
  const markPaid = async orderNo => { Modal.confirm({ title: '标记支付成功', content: '将手动标记该未支付订单为已支付并提交预占库存，确认操作？', onOk: async () => { await shipmentApi.markPaid(orderNo); message.success('订单已标记为支付成功'); loadAllOrders() } }) }
  const channelQuery = async orderNo => {
    setChannelQueryRow(null); setChannelQueryLoading(true); setPayAttempts([])
    try {
      const r = await shipmentApi.channelQuery(orderNo)
      setChannelQueryRow(r.data)
      shipmentApi.paymentOrders(orderNo).then(pr => setPayAttempts(pr.data || []))
      loadAllOrders()
    } finally { setChannelQueryLoading(false) }
  }
  const prettyRaw = raw => { try { return JSON.stringify(JSON.parse(raw), null, 2) } catch { return raw } }
  const poCols = [
    { title: '支付单号', dataIndex: 'paymentNo', width: 170 },
    { title: '渠道', dataIndex: 'channel', width: 80 },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: s => <Tag color={s === 'SUCCESS' ? 'green' : s === 'PAYING' ? 'orange' : undefined}>{s}</Tag>
    },
    { title: '渠道交易号', dataIndex: 'channelOrderNo', render: v => v || '-', ellipsis: true },
    { title: '请求金额', width: 90, render: (_, r) => `¥${((r.requestAmount || 0) / 100).toFixed(2)}` },
    { title: '实付金额', width: 90, render: (_, r) => r.paidAmount ? `¥${(r.paidAmount / 100).toFixed(2)}` : '-' },
    { title: '发起时间', width: 150, render: (_, r) => r.createTime ? new Date(r.createTime).toLocaleString() : '-' },
    { title: '支付时间', width: 150, render: (_, r) => r.paidTime ? new Date(r.paidTime).toLocaleString() : '-' }
  ]
  const openDetail = d => { setDetailRow(d); setPaAmount(0); setPaReason('差价补偿') }
  const submitPriceAdjust = async () => {
    if (!detailRow || !Number(paAmount)) { message.error('请填写退款金额（分）'); return }
    await refundApi.priceAdjustment({ orderNo: detailRow.order.orderNo.trim(), amount: Number(paAmount), reason: paReason.trim() || '差价补偿' })
    message.success('差价退款已创建并冻结额度'); setPaAmount(0); loadAllOrders()
  }
  const allOrderCols = [
    { title: '订单号', render: (_, d) => <a onClick={() => openDetail(d)}>{d.order.orderNo}</a> },
    { title: '用户ID', render: (_, d) => d.order.userId },
    { title: '商品', render: (_, d) => d.items.map(i => <div key={i.id}>{i.productTitle} × {i.quantity}</div>) },
    { title: '金额', render: (_, d) => `¥${((d.order.totalFee || 0) / 100).toFixed(2)}` },
    { title: '支付状态', render: (_, d) => <Tag color={d.order.payStatus === 'PAID' ? 'green' : 'orange'}>{PAY_LABEL[d.order.payStatus] || d.order.payStatus}</Tag> },
    { title: '订单状态', render: (_, d) => d.order.orderStatus },
    { title: '履约状态', render: (_, d) => <Tag>{FULFILLMENT_LABEL[d.order.fulfillmentStatus] || d.order.fulfillmentStatus}</Tag> },
    { title: '退款状态', render: (_, d) => <Tag color={d.order.refundStatus === 'REFUNDING' ? 'red' : undefined}>{d.order.refundStatus === 'NONE' ? '' : d.order.refundStatus}</Tag> },
    { title: '创建时间', render: (_, d) => d.order.createTime ? new Date(d.order.createTime).toLocaleString() : '-' },
    {
      title: '操作', width: 200, render: (_, d) => {
        const canClose = d.order.payStatus === 'UNPAID' && (d.order.orderStatus === '未支付' || d.order.orderStatus === '超时已关闭')
        const canMarkPaid = d.order.payStatus === 'UNPAID' && d.order.orderStatus === '未支付'
        return <Space>
          <Button size="small" onClick={() => openDetail(d)}>详情</Button>
          {canClose && <Button danger size="small" onClick={() => forceClose(d.order.orderNo)}>强制关单</Button>}
          {canMarkPaid && <Button type="primary" size="small" onClick={() => markPaid(d.order.orderNo)}>标记已付</Button>}
          <Button size="small" type="primary" ghost onClick={() => channelQuery(d.order.orderNo)}>渠道查单</Button>
        </Space>
      }
    }
  ]
  const itemCols = [
    { title: '商品', dataIndex: 'productTitle' },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    { title: '快照单价(分)', dataIndex: 'unitPrice', width: 110 },
    { title: '小计(分)', render: (_, i) => (Number(i.unitPrice || 0) * Number(i.quantity || 0)) }
  ]
  const o = detailRow?.order
  const canClose = o && o.payStatus === 'UNPAID' && (o.orderStatus === '未支付' || o.orderStatus === '超时已关闭')
  const canMarkPaid = o && o.payStatus === 'UNPAID' && o.orderStatus === '未支付'
  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>订单管理</Typography.Title>
    <Card>
    <Space style={{ marginBottom: 16 }} wrap>
      <Select placeholder="支付状态" allowClear style={{ width: 120 }} value={orderFilter.payStatus || undefined} onChange={v => setOrderFilter({ ...orderFilter, payStatus: v || '' })}>
        <Select.Option value="UNPAID">待支付</Select.Option>
        <Select.Option value="PAID">已支付</Select.Option>
      </Select>
      <Select placeholder="订单生命周期" allowClear style={{ width: 130 }} value={orderFilter.orderStatus || undefined} onChange={v => setOrderFilter({ ...orderFilter, orderStatus: v || '' })}>
        <Select.Option value="WAIT_PAY">待支付</Select.Option>
        <Select.Option value="ACTIVE">已激活</Select.Option>
        <Select.Option value="CLOSED">已关闭</Select.Option>
      </Select>
      <Select placeholder="履约状态" allowClear style={{ width: 120 }} value={orderFilter.fulfillmentStatus || undefined} onChange={v => setOrderFilter({ ...orderFilter, fulfillmentStatus: v || '' })}>
        <Select.Option value="WAIT_SHIP">待发货</Select.Option>
        <Select.Option value="SHIPPED">已发货</Select.Option>
        <Select.Option value="RECEIVED">已收货</Select.Option>
        <Select.Option value="CANCELLED">已取消</Select.Option>
      </Select>
      <Input allowClear placeholder="订单号" style={{ width: 190 }} value={orderNo} onChange={e => setOrderNo(e.target.value)} />
      <Input allowClear placeholder="用户编号" style={{ width: 130 }} value={userId} onChange={e => setUserId(e.target.value)} />
      <DatePicker.RangePicker value={dateRange} onChange={setDateRange} format="YYYY-MM-DD" placeholder={['开始日期', '结束日期']} />
      <Button type="primary" onClick={loadAllOrders}>查询</Button>
    </Space>
    <Table rowKey={d => d.order.orderNo} dataSource={allOrders} columns={allOrderCols} size="small" />
    <Drawer open={!!detailRow} width={760} onClose={() => setDetailRow(null)}
      title={o ? `订单详情 - ${o.orderNo}` : '订单详情'}
      footer={<div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        {canClose && <Button danger onClick={() => forceClose(o.orderNo)}>强制关单</Button>}
        {canMarkPaid && <Button type="primary" onClick={() => markPaid(o.orderNo)}>标记已付</Button>}
        <Button onClick={() => setDetailRow(null)}>关闭</Button>
      </div>}>
      {o && <div>
        <Descriptions column={2} bordered size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="订单号">{o.orderNo}</Descriptions.Item>
          <Descriptions.Item label="用户ID">{o.userId}</Descriptions.Item>
          <Descriptions.Item label="支付状态"><Tag color={o.payStatus === 'PAID' ? 'green' : 'orange'}>{PAY_LABEL[o.payStatus] || o.payStatus}</Tag></Descriptions.Item>
          <Descriptions.Item label="订单状态">{o.orderStatus}</Descriptions.Item>
          <Descriptions.Item label="履约状态">{FULFILLMENT_LABEL[o.fulfillmentStatus] || o.fulfillmentStatus}</Descriptions.Item>
          <Descriptions.Item label="退款状态">{o.refundStatus === 'NONE' ? '-' : o.refundStatus}</Descriptions.Item>
          <Descriptions.Item label="订单金额">¥{((o.totalFee || 0) / 100).toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="支付方式">{o.paymentType || '-'}</Descriptions.Item>
          <Descriptions.Item label="收货人">{o.receiverName || '-'}</Descriptions.Item>
          <Descriptions.Item label="收货电话">{o.receiverPhone || '-'}</Descriptions.Item>
          <Descriptions.Item label="收货地址" span={2}>{o.receiverAddress || '-'}</Descriptions.Item>
          <Descriptions.Item label="创建时间">{o.createTime ? new Date(o.createTime).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="支付时间">{o.paidTime ? new Date(o.paidTime).toLocaleString() : '-'}</Descriptions.Item>
        </Descriptions>
        <h4>订单明细</h4>
        <Table rowKey="id" dataSource={detailRow.items || []} columns={itemCols} pagination={false} size="small" style={{ marginBottom: 20 }} />
        <h4>差价退款</h4>
        <p style={{ fontSize: 12, color: '#8c8c8c' }}>管理员手填金额，受订单可退额度约束；提交后创建退款单并冻结额度。</p>
        <Space wrap>
          <InputNumber min={1} placeholder="金额(分)" value={paAmount || undefined} onChange={v => setPaAmount(v || 0)} style={{ width: 160 }} addonBefore="金额(分)" />
          <Input placeholder="退款原因" value={paReason} maxLength={255} onChange={e => setPaReason(e.target.value)} style={{ width: 240 }} />
          <Button type="primary" onClick={submitPriceAdjust}>提交差价退款</Button>
        </Space>
      </div>}
    </Drawer>
    <Modal
      open={!!channelQueryRow || channelQueryLoading}
      width={860}
      title={channelQueryRow ? `渠道查单 - ${channelQueryRow.orderNo}` : '渠道查单'}
      onCancel={() => setChannelQueryRow(null)}
      footer={<Space>
        <Button onClick={() => setChannelQueryRow(null)}>关闭</Button>
        {channelQueryRow && <Button type="primary" loading={channelQueryLoading} onClick={() => channelQuery(channelQueryRow.orderNo)}>重新查单</Button>}
      </Space>}
    >
      {channelQueryRow && <div>
        <h4 style={{ marginTop: 0 }}>支付尝试记录（本订单全部渠道支付单）</h4>
        <Table rowKey="id" dataSource={payAttempts} columns={poCols} pagination={false} size="small" style={{ marginBottom: 16 }} />
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="渠道">{channelQueryRow.channelCode}</Descriptions.Item>
          <Descriptions.Item label="渠道状态">{channelQueryRow.channelTradeState}</Descriptions.Item>
          <Descriptions.Item label="状态说明" span={2}>{channelQueryRow.channelTradeStateDesc}</Descriptions.Item>
          <Descriptions.Item label="本地订单状态">{channelQueryRow.localOrderStatusBefore} → {channelQueryRow.localOrderStatusAfter}</Descriptions.Item>
          <Descriptions.Item label="查单后支付状态">{channelQueryRow.localPayStatusAfter}</Descriptions.Item>
          <Descriptions.Item label="是否同步" span={2}>
            <Tag color={channelQueryRow.synced ? 'green' : 'default'}>{channelQueryRow.synced ? '已推进本地订单' : '本地状态未变更'}</Tag>
          </Descriptions.Item>
        </Descriptions>
        {channelQueryRow.channelRawBody && <div>
          <h4>渠道原始报文</h4>
          <pre style={{ maxHeight: 260, overflow: 'auto', background: '#f5f7fa', padding: 8, fontSize: 12, borderRadius: 4 }}>{prettyRaw(channelQueryRow.channelRawBody)}</pre>
        </div>}
      </div>}
    </Modal>
    </Card>
  </div>
}
