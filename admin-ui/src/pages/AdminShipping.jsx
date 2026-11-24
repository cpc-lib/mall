import { useEffect, useState } from 'react'
import { Button, Card, message, Table, Typography } from 'antd'
import shipmentApi from '@/api/shipment'

export default function AdminShipping() {
  const [waitShip, setWaitShip] = useState([])
  const [loading, setLoading] = useState(false)
  const [page, setPage] = useState(1)
  const [size, setSize] = useState(10)
  const [total, setTotal] = useState(0)
  const load = (p = page, s = size) => {
    setLoading(true)
    shipmentApi.waitShipList(p, s).then(r => {
      setWaitShip(r.data?.records || [])
      setTotal(r.data?.total || 0)
      setPage(r.data?.page || p)
      setSize(r.data?.size || s)
    }).catch(() => setWaitShip([]))
      .finally(() => setLoading(false))
  }
  useEffect(() => { load(1) }, [])
  const ship = async orderNo => {
    const r = await shipmentApi.shipOrder(orderNo)
    message.success(`发货成功，运单号 ${r.data?.trackingNo || '-'}`); load()
  }
  const waitShipCols = [
    { title: '订单号', dataIndex: 'orderNo' }, { title: '标题', dataIndex: 'title' },
    { title: '金额', render: (_, r) => `¥${((r.totalFee || 0) / 100).toFixed(2)}` },
    { title: '支付方式', dataIndex: 'paymentType' },
    { title: '支付时间', dataIndex: 'paidTime', render: v => v ? new Date(v).toLocaleString() : '-' },
    { title: '操作', render: (_, r) => <Button type="primary" onClick={() => ship(r.orderNo)}>模拟发货</Button> }
  ]
  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>订单发货</Typography.Title>
    <Card styles={{ body: { padding: '8px 20px 16px' } }}>
      <Table rowKey="orderNo" dataSource={waitShip} columns={waitShipCols} loading={loading}
        pagination={{
          current: page,
          pageSize: size,
          total,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          showTotal: t => `共 ${t} 条`,
          onChange: (p, s) => load(p, s)
        }} />
    </Card>
  </div>
}
