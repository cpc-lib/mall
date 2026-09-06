import { useEffect, useState } from 'react'
import { Button, message, Table } from 'antd'
import shipmentApi from '@/api/shipment'

// 订单发货：待发货列表 + 模拟发货
export default function AdminShipping() {
  const [waitShip, setWaitShip] = useState([])
  const load = () => { shipmentApi.waitShipList().then(r => setWaitShip(r.data || [])).catch(() => setWaitShip([])) }
  useEffect(load, [])
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
    <h2 className="adm-page-title">订单发货</h2>
    <div className="adm-card" style={{ padding: '8px 20px 16px' }}>
      <Table rowKey="orderNo" dataSource={waitShip} columns={waitShipCols} />
    </div>
  </div>
}
