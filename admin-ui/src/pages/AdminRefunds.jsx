import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Drawer, message, Table, Tag, Typography } from 'antd'
import refundApi from '@/api/refundApply'
import { REFUND_STATUS_LABEL, REFUND_TYPE_LABEL } from '@/utils/statusLabels'

export default function AdminRefunds() {
  const [refunds, setRefunds] = useState([])
  const [detail, setDetail] = useState(null)
  const load = () => {
    refundApi.all().then(r => {
      const list = r.data || []
      setRefunds(list)
      setDetail(prev => prev ? (list.find(x => x.apply.refundNo === prev.apply.refundNo) || prev) : null)
    })
  }
  useEffect(load, [])
  const openDetail = v => setDetail(v)
  const action = async (fn, okMsg) => { await fn(); message.success(okMsg); load() }
  const queryStatus = async refundNo => {
    try {
      message.loading({ content: '查询中...', key: 'rq', duration: 0 })
      const r = await refundApi.queryStatus(refundNo)
      message.destroy('rq')
      message.success(`查询完成，状态：${r.data?.apply?.status || '-'}`); load()
    } catch (e) { message.destroy('rq'); message.error('查询失败：' + (e?.message || '渠道接口异常')) }
  }
  const refundCols = [
    { title: '退款号', render: (_, v) => <a onClick={() => openDetail(v)}>{v.apply.refundNo}</a> },
    { title: '订单号', render: (_, v) => v.apply.orderNo },
    { title: '类型', render: (_, v) => REFUND_TYPE_LABEL[v.apply.refundType] || v.apply.refundType },
    { title: '金额', render: (_, v) => `¥${((v.apply.refundAmount || 0) / 100).toFixed(2)}` },
    { title: '状态', render: (_, v) => <Tag color={v.apply.status === 'FAILED' ? 'red' : v.apply.status === 'SUCCESS' ? 'green' : 'blue'}>{REFUND_STATUS_LABEL[v.apply.status] || v.apply.status}</Tag> },
    { title: '失败原因', width: 200, render: (_, v) => v.apply.status === 'FAILED' ? <span style={{ color: '#f50', fontSize: 12 }}>{v.failReason || '-'}</span> : '-' },
    { title: '明细', render: (_, v) => v.items.map(i => <div key={i.id}>item#{i.orderItemId} × {i.refundQty}</div>) },
    { title: '操作', width: 90, render: (_, v) => <Button size="small" onClick={() => openDetail(v)}>详情</Button> }
  ]
  const itemCols = [
    { title: '明细项ID', dataIndex: 'orderItemId', width: 100, render: v => `item#${v}` },
    { title: '退款数量', dataIndex: 'refundQty', width: 90 },
    { title: '快照单价(分)', dataIndex: 'unitPrice', width: 110 },
    { title: '退款小计(分)', render: (_, i) => Number(i.unitPrice || 0) * Number(i.refundQty || 0) }
  ]
  const a = detail?.apply
  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>退款受理</Typography.Title>
    <Card styles={{ body: { padding: '8px 20px 16px' } }}>
      <Table rowKey={v => v.apply.refundNo} dataSource={refunds} columns={refundCols} />
    </Card>
    <Drawer open={!!detail} width={720} onClose={() => setDetail(null)}
      title={a ? `退款详情 - ${a.refundNo}` : '退款详情'}
      footer={a ? <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        {a.status === 'APPLYING' && <Button danger onClick={() => action(() => refundApi.reject(a.refundNo, '管理员拒绝'), '已拒绝并释放冻结额度')}>拒绝</Button>}
        {a.status === 'APPLYING' && <Button type="primary" onClick={() => action(() => refundApi.accept(a.refundNo, '管理员受理'), '已受理')}>受理</Button>}
        {a.status === 'APPROVED' && a.refundType === 'RETURN_AND_REFUND' && <Button type="primary" onClick={() => action(() => refundApi.confirmReturn(a.refundNo, '退货签收质检通过'), '已签收确认，库存回补并发起渠道退款')}>退货签收确认</Button>}
        {a.status === 'FAILED' && <Button onClick={() => action(() => refundApi.retry(a.refundNo), '已重新发起渠道退款')}>重试退款</Button>}
        {(a.status === 'REFUNDING' || a.status === 'FAILED') && <Button onClick={() => queryStatus(a.refundNo)}>查询状态</Button>}
        <Button onClick={() => setDetail(null)}>关闭</Button>
      </div> : null}>
      {a && <div>
        <Descriptions column={2} bordered size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="退款号">{a.refundNo}</Descriptions.Item>
          <Descriptions.Item label="订单号">{a.orderNo}</Descriptions.Item>
          <Descriptions.Item label="退款类型">{REFUND_TYPE_LABEL[a.refundType] || a.refundType}</Descriptions.Item>
          <Descriptions.Item label="状态"><Tag color={a.status === 'FAILED' ? 'red' : a.status === 'SUCCESS' ? 'green' : 'blue'}>{REFUND_STATUS_LABEL[a.status] || a.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="退款金额">¥{((a.refundAmount || 0) / 100).toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="申请时间">{a.createTime ? new Date(a.createTime).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="退款原因" span={2}>{a.reason || '-'}</Descriptions.Item>
          {detail.failReason && <Descriptions.Item label="失败原因" span={2}><span style={{ color: '#f50' }}>{detail.failReason}</span></Descriptions.Item>}
        </Descriptions>
        <h4>退款明细</h4>
        <Table rowKey="id" dataSource={detail.items || []} columns={itemCols} pagination={false} size="small" />
      </div>}
    </Drawer>
  </div>
}
