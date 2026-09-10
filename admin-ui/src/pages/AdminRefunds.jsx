import { useEffect, useState } from 'react'
import { Alert, Button, Card, Descriptions, Drawer, Input, Modal, Radio, message, Table, Tag, Typography } from 'antd'
import refundApi from '@/api/refundApply'
import { FULFILLMENT_LABEL, REFUND_STATUS_LABEL, REFUND_STATUS_COLOR, REFUND_TYPE_LABEL } from '@/utils/statusLabels'

export default function AdminRefunds() {
  const [refunds, setRefunds] = useState([])
  const [detail, setDetail] = useState(null)
  const [acceptTarget, setAcceptTarget] = useState(null)
  const [goodsDisposition, setGoodsDisposition] = useState('')
  const [acceptRemark, setAcceptRemark] = useState('')
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
  const beginAccept = v => {
    if (!v?.goodsDispositionRequired) {
      return action(() => refundApi.accept(v.apply.refundNo, '管理员受理', null), '已受理')
    }
    setAcceptTarget(v)
    setGoodsDisposition('')
    setAcceptRemark('')
  }
  const submitAccept = async () => {
    if (!acceptTarget) return
    if (!goodsDisposition) {
      message.warning('请先确认商品是丢失还是已全部回收')
      return
    }
    const label = goodsDisposition === 'RECOVERED' ? '商品已全部回收' : '商品丢失/无法回收'
    await action(
      () => refundApi.accept(acceptTarget.apply.refundNo, acceptRemark.trim() || label, goodsDisposition),
      `已确认${label}并受理退款`
    )
    setAcceptTarget(null)
    setGoodsDisposition('')
    setAcceptRemark('')
  }
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
    { title: '状态', render: (_, v) => <Tag color={REFUND_STATUS_COLOR[v.apply.status] || 'blue'}>{REFUND_STATUS_LABEL[v.apply.status] || v.apply.status}</Tag> },
    { title: '商品去向', width: 120, render: (_, v) => v.apply.goodsDisposition === 'RECOVERED' ? <Tag color="green">已全部回收</Tag> : v.apply.goodsDisposition === 'LOST' ? <Tag color="red">丢失/无法回收</Tag> : v.goodsDispositionRequired ? <Tag color="orange">待确认</Tag> : '-' },
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
        {a.status === 'APPLYING' && <Button type="primary" onClick={() => beginAccept(detail)}>{detail.goodsDispositionRequired ? '确认商品去向并受理' : '受理'}</Button>}
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
          <Descriptions.Item label="状态"><Tag color={REFUND_STATUS_COLOR[a.status] || 'blue'}>{REFUND_STATUS_LABEL[a.status] || a.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="退款金额">¥{((a.refundAmount || 0) / 100).toFixed(2)}</Descriptions.Item>
          <Descriptions.Item label="履约状态">{FULFILLMENT_LABEL[detail.fulfillmentStatus] || detail.fulfillmentStatus || '-'}</Descriptions.Item>
          <Descriptions.Item label="商品去向">{a.goodsDisposition === 'RECOVERED' ? '已全部回收' : a.goodsDisposition === 'LOST' ? '丢失/无法回收' : detail.goodsDispositionRequired ? '待管理员确认' : '-'}</Descriptions.Item>
          <Descriptions.Item label="申请时间">{a.createTime ? new Date(a.createTime).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="退款原因" span={2}>{a.reason || '-'}</Descriptions.Item>
          {detail.failReason && <Descriptions.Item label="失败原因" span={2}><span style={{ color: '#f50' }}>{detail.failReason}</span></Descriptions.Item>}
        </Descriptions>
        <h4>退款明细</h4>
        <Table rowKey="id" dataSource={detail.items || []} columns={itemCols} pagination={false} size="small" />
      </div>}
    </Drawer>
    <Modal
      open={!!acceptTarget}
      title="确认商品去向并受理退款"
      okText="确认并受理"
      cancelText="取消"
      onOk={submitAccept}
      onCancel={() => { setAcceptTarget(null); setGoodsDisposition(''); setAcceptRemark('') }}
      okButtonProps={{ disabled: !goodsDisposition }}
    >
      <Alert
        type="warning"
        showIcon
        message="已发货退款必须先确认商品最终去向"
        description="该决定会直接影响库存四桶结转。确认后系统才会发起渠道退款，请根据物流/仓库核实结果选择。"
        style={{ marginBottom: 16 }}
      />
      <Radio.Group value={goodsDisposition} onChange={e => setGoodsDisposition(e.target.value)} style={{ display: 'grid', gap: 12, width: '100%' }}>
        <Radio value="LOST">商品丢失 / 无法回收（锁定库存 → 货损库存）</Radio>
        <Radio value="RECOVERED">商品已全部回收（锁定库存 → 可售库存）</Radio>
      </Radio.Group>
      <Input.TextArea
        value={acceptRemark}
        onChange={e => setAcceptRemark(e.target.value)}
        placeholder="处置备注（选填，例如物流确认丢失、仓库已签收入库）"
        maxLength={255}
        rows={3}
        style={{ marginTop: 16 }}
      />
    </Modal>
  </div>
}
