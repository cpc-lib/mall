import { useEffect, useState } from 'react'
import { Button, Card, DatePicker, Descriptions, Drawer, Input, InputNumber, message, Modal, Select, Space, Table, Tag, Typography } from 'antd'
import shipmentApi from '@/api/shipment'
import refundApi from '@/api/refundApply'
import { PAY_LABEL, FULFILLMENT_LABEL, PAY_COLOR, PAYMENT_STATUS_COLOR, ORDER_REFUND_COLOR, ORDER_REFUND_LABEL, SHIPMENT_LABEL } from '@/utils/statusLabels'

export default function AdminOrders() {
  const [allOrders, setAllOrders] = useState([])
  const [listLoading, setListLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(20)
  const [total, setTotal] = useState(0)
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
  const [detailPayAttempts, setDetailPayAttempts] = useState([])
  const [detailPayLoaded, setDetailPayLoaded] = useState(false)
  const [detailPayLoading, setDetailPayLoading] = useState(false)
  const [detailRefundOrders, setDetailRefundOrders] = useState([])
  const [detailRefundLoaded, setDetailRefundLoaded] = useState(false)
  const [detailRefundLoading, setDetailRefundLoading] = useState(false)
  const [detailShipment, setDetailShipment] = useState(null)
  const [shipmentLoading, setShipmentLoading] = useState(false)
  const loadAllOrders = (nextPage = page, nextSize = size) => {
    const params = { page: nextPage, size: nextSize }
    if (orderFilter.payStatus) params.payStatus = orderFilter.payStatus
    if (orderFilter.orderStatus) params.orderStatus = orderFilter.orderStatus
    if (orderFilter.fulfillmentStatus) params.fulfillmentStatus = orderFilter.fulfillmentStatus
    if (orderNo.trim()) params.orderNo = orderNo.trim()
    if (userId.trim()) params.userId = userId.trim()
    if (dateRange && dateRange.length === 2) {
      params.startTime = dateRange[0].format('YYYY-MM-DD')
      params.endTime = dateRange[1].format('YYYY-MM-DD')
    }
    setListLoading(true)
    shipmentApi.allOrders(params).then(r => {
      const data = r.data || {}
      const list = data.records || []
      setAllOrders(list)
      setTotal(data.total || 0)
      setPage(data.page || nextPage)
      setSize(data.size || nextSize)
      setDetailRow(prev => {
        if (!prev) return null
        return list.find(x => x.order.orderNo === prev.order.orderNo) || prev
      })
    }).catch(() => { setAllOrders([]); setTotal(0) })
      .finally(() => setListLoading(false))
  }
  useEffect(() => { loadAllOrders(1, 20) }, [])
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
    { title: '渠道', dataIndex: 'channel', width: 80, render: v => v === 'OFFLINE' ? '线下' : v },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: s => <Tag color={PAYMENT_STATUS_COLOR[s]}>{s}</Tag>
    },
    { title: '渠道交易号', dataIndex: 'channelOrderNo', render: v => v || '-', ellipsis: true },
    { title: '请求金额', width: 90, render: (_, r) => `¥${((r.requestAmount || 0) / 100).toFixed(2)}` },
    { title: '实付金额', width: 90, render: (_, r) => r.paidAmount ? `¥${(r.paidAmount / 100).toFixed(2)}` : '-' },
    { title: '发起时间', width: 150, render: (_, r) => r.createTime ? new Date(r.createTime).toLocaleString() : '-' },
    { title: '支付时间', width: 150, render: (_, r) => r.paidTime ? new Date(r.paidTime).toLocaleString() : '-' }
  ]
  const refundCols = [
    { title: '退款单号', render: (_, r) => r.apply?.refundNo || '-', width: 170 },
    { title: '类型', render: (_, r) => r.apply?.refundType || '-', width: 110 },
    { title: '来源', render: (_, r) => r.apply?.applyType || '-', width: 80 },
    {
      title: '状态', width: 110,
      render: (_, r) => {
        const s = r.apply?.status
        const color = s === 'SUCCESS' ? 'green' : s === 'FAILED' ? 'red' : s === 'REJECTED' || s === 'CANCELLED' ? 'default' : 'orange'
        return s ? <Tag color={color}>{s}</Tag> : '-'
      }
    },
    { title: '金额', width: 90, render: (_, r) => r.apply?.refundAmount != null ? `¥${(r.apply.refundAmount / 100).toFixed(2)}` : '-' },
    { title: '原因', render: (_, r) => r.apply?.reason || '-', ellipsis: true },
    { title: '创建时间', width: 150, render: (_, r) => r.apply?.createTime ? new Date(r.apply.createTime).toLocaleString() : '-' },
    { title: '成功时间', width: 150, render: (_, r) => r.apply?.successTime ? new Date(r.apply.successTime).toLocaleString() : '-' }
  ]
  const openDetail = d => {
    setDetailRow(d); setPaAmount(0); setPaReason('差价补偿')
    setDetailShipment(null)
    setDetailPayAttempts([]); setDetailPayLoaded(false)
    setDetailRefundOrders([]); setDetailRefundLoaded(false)
  }
  const loadDetailPayments = async orderNo => {
    if (detailPayLoading) return
    setDetailPayLoading(true)
    try {
      const r = await shipmentApi.paymentOrders(orderNo)
      setDetailPayAttempts(r.data || [])
      setDetailPayLoaded(true)
    } finally {
      setDetailPayLoading(false)
    }
  }
  const loadDetailRefunds = async orderNo => {
    if (detailRefundLoading) return
    setDetailRefundLoading(true)
    try {
      const r = await shipmentApi.refundOrders(orderNo)
      setDetailRefundOrders(r.data || [])
      setDetailRefundLoaded(true)
    } finally {
      setDetailRefundLoading(false)
    }
  }
  const loadShipment = async orderNo => {
    if (shipmentLoading) return
    setShipmentLoading(true)
    try {
      const r = await shipmentApi.getAdminShipment(orderNo)
      setDetailShipment(r.data || { shipped: false })
    } finally {
      setShipmentLoading(false)
    }
  }
  const submitPriceAdjust = async () => {
    if (!detailRow || !Number(paAmount)) { message.error('请填写退款金额（分）'); return }
    await refundApi.priceAdjustment({ orderNo: detailRow.order.orderNo.trim(), amount: Number(paAmount), reason: paReason.trim() || '差价补偿' })
    message.success('差价退款已创建并冻结额度'); setPaAmount(0); loadAllOrders()
  }
  const allOrderCols = [
    { title: '订单号', render: (_, d) => <a onClick={() => openDetail(d)}>{d.order.orderNo}</a> },
    { title: '用户ID', render: (_, d) => d.order.userId },
    { title: '商品', render: (_, d) => d.items.map(i => <div key={i.id}>{i.productTitle}{i.skuSpecText && <Tag style={{ marginLeft: 4 }}>{i.skuSpecText}</Tag>} × {i.quantity}</div>) },
    { title: '金额', render: (_, d) => `¥${((d.order.totalFee || 0) / 100).toFixed(2)}` },
    { title: '支付状态', render: (_, d) => <Tag color={PAY_COLOR[d.order.payStatus] || 'orange'}>{PAY_LABEL[d.order.payStatus] || d.order.payStatus}</Tag> },
    { title: '订单状态', render: (_, d) => d.order.orderStatus },
    { title: '履约状态', render: (_, d) => <Tag>{FULFILLMENT_LABEL[d.order.fulfillmentStatus] || d.order.fulfillmentStatus}</Tag> },
    { title: '退款状态', render: (_, d) => <Tag color={ORDER_REFUND_COLOR[d.order.refundStatus]}>{ORDER_REFUND_LABEL[d.order.refundStatus] ?? d.order.refundStatus}</Tag> },
    { title: '创建时间', render: (_, d) => d.order.createTime ? new Date(d.order.createTime).toLocaleString() : '-' },
    {
      title: '操作', width: 200, render: (_, d) => {
        const canClose = (d.order.payStatus === 'UNPAID' || d.order.payStatus === 'PAYING') && (d.order.orderStatus === '未支付' || d.order.orderStatus === '超时已关闭')
        const canMarkPaid = (d.order.payStatus === 'UNPAID' || d.order.payStatus === 'PAYING') && d.order.orderStatus === '未支付'
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
    {
      title: '商品 / 规格', dataIndex: 'productTitle',
      render: (t, i) => <span>{t}{i.skuSpecText && <Tag color="blue" style={{ marginLeft: 6 }}>{i.skuSpecText}</Tag>}</span>
    },
    { title: '数量', dataIndex: 'quantity', width: 80 },
    { title: '快照单价(分)', dataIndex: 'unitPrice', width: 110 },
    { title: '小计(分)', render: (_, i) => (Number(i.unitPrice || 0) * Number(i.quantity || 0)) }
  ]
  const o = detailRow?.order
  const canClose = o && (o.payStatus === 'UNPAID' || o.payStatus === 'PAYING') && (o.orderStatus === '未支付' || o.orderStatus === '超时已关闭')
  const canMarkPaid = o && (o.payStatus === 'UNPAID' || o.payStatus === 'PAYING') && o.orderStatus === '未支付'
  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>订单管理</Typography.Title>
    <Card>
    <Space style={{ marginBottom: 16 }} wrap>
      <Select placeholder="支付状态" allowClear style={{ width: 120 }} value={orderFilter.payStatus || undefined} onChange={v => setOrderFilter({ ...orderFilter, payStatus: v || '' })}>
        <Select.Option value="UNPAID">待支付</Select.Option>
        <Select.Option value="PAYING">支付中</Select.Option>
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
      <Button type="primary" onClick={() => loadAllOrders(1, size)}>查询</Button>
    </Space>
    <Table
      rowKey={d => d.order.orderNo}
      dataSource={allOrders}
      columns={allOrderCols}
      size="small"
      loading={listLoading}
      pagination={{
        current: page,
        pageSize: size,
        total,
        showSizeChanger: true,
        pageSizeOptions: [10, 20, 50, 100],
        showTotal: t => `共 ${t} 条`,
        onChange: (nextPage, nextSize) => loadAllOrders(nextPage, nextSize)
      }}
    />
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
          <Descriptions.Item label="支付状态"><Tag color={PAY_COLOR[o.payStatus] || 'orange'}>{PAY_LABEL[o.payStatus] || o.payStatus}</Tag></Descriptions.Item>
          <Descriptions.Item label="订单状态">{o.orderStatus}</Descriptions.Item>
          <Descriptions.Item label="履约状态">{FULFILLMENT_LABEL[o.fulfillmentStatus] || o.fulfillmentStatus}</Descriptions.Item>
          <Descriptions.Item label="退款状态">{ORDER_REFUND_LABEL[o.refundStatus] || '-'}</Descriptions.Item>
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
        <h4>物流信息</h4>
        {!detailShipment && <Button size="small" loading={shipmentLoading} onClick={() => loadShipment(o.orderNo)}>查看物流信息</Button>}
        {detailShipment && !detailShipment.shipped && <p style={{ fontSize: 12, color: '#8c8c8c', margin: 0 }}>订单尚未发货</p>}
        {detailShipment?.shipped && <div style={{ marginBottom: 20 }}>
          <Descriptions column={2} bordered size="small" style={{ marginBottom: 12 }}>
            <Descriptions.Item label="物流单号">{detailShipment.shipmentNo || '-'}</Descriptions.Item>
            <Descriptions.Item label="物流状态"><Tag color="blue">{SHIPMENT_LABEL[detailShipment.status] || detailShipment.status || '-'}</Tag></Descriptions.Item>
            <Descriptions.Item label="物流公司">{detailShipment.logisticsCompany || '-'}</Descriptions.Item>
            <Descriptions.Item label="运单号">{detailShipment.trackingNo || '-'}</Descriptions.Item>
          </Descriptions>
          <div style={{ fontSize: 12, lineHeight: '22px' }}>
            {Object.entries(detailShipment.timeline || {}).filter(([, v]) => v).map(([k, v]) => (
              <div key={k}>{SHIPMENT_LABEL[k.toUpperCase()] || k}：{new Date(v).toLocaleString()}</div>
            ))}
          </div>
        </div>}
        <h4>差价退款</h4>
        <p style={{ fontSize: 12, color: '#8c8c8c' }}>管理员手填金额，受订单可退额度约束；提交后创建退款单并冻结额度。</p>
        <Space wrap>
          <InputNumber min={1} placeholder="金额(分)" value={paAmount || undefined} onChange={v => setPaAmount(v || 0)} style={{ width: 160 }} addonBefore="金额(分)" />
          <Input placeholder="退款原因" value={paReason} maxLength={255} onChange={e => setPaReason(e.target.value)} style={{ width: 240 }} />
          <Button type="primary" onClick={submitPriceAdjust}>提交差价退款</Button>
        </Space>
        <h4 style={{ marginTop: 24 }}>支付尝试记录</h4>
        {!detailPayLoaded && <Button size="small" loading={detailPayLoading} onClick={() => loadDetailPayments(o.orderNo)}>查看支付尝试记录</Button>}
        {detailPayLoaded && <Table rowKey="id" dataSource={detailPayAttempts} columns={poCols} pagination={false} size="small" style={{ marginBottom: 16 }} />}
        <h4>退款单记录</h4>
        {!detailRefundLoaded && <Button size="small" loading={detailRefundLoading} onClick={() => loadDetailRefunds(o.orderNo)}>查看退款单记录</Button>}
        {detailRefundLoaded && <Table rowKey={(r) => r.apply?.refundNo || r.apply?.id} dataSource={detailRefundOrders} columns={refundCols} pagination={false} size="small" locale={{ emptyText: '暂无退款记录' }} />}
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
