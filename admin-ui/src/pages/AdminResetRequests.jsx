import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Drawer, Input, message, Modal, Space, Table, Tag, Typography } from 'antd'
import authApi from '@/api/auth'
import { RESET_STATUS_COLOR, RESET_STATUS_LABEL } from '@/utils/statusLabels'

export default function AdminResetRequests() {
  const [resetRequests, setResetRequests] = useState([])
  const [detail, setDetail] = useState(null)
  const [handleRemark, setHandleRemark] = useState('')
  const [rejectRemark, setRejectRemark] = useState('')
  const [generatedPwd, setGeneratedPwd] = useState('')
  const load = () => {
    authApi.passwordResetRequests().then(r => {
      const list = r.data || []
      setResetRequests(list)
      setDetail(prev => prev ? (list.find(x => x.id === prev.id) || prev) : null)
    }).catch(() => setResetRequests([]))
  }
  useEffect(load, [])
  const openDetail = r => { setDetail(r); setHandleRemark(''); setRejectRemark('') }
  const handleResetRequest = async () => {
    const r = await authApi.handlePasswordResetRequest(detail.id, handleRemark.trim())
    setGeneratedPwd(r.data?.newPassword || '')
    setDetail(null)
    load()
  }
  const rejectResetRequest = async () => {
    await authApi.rejectPasswordResetRequest(detail.id, rejectRemark.trim())
    message.success('已拒绝该申请')
    setDetail(null)
    load()
  }
  const prCols = [
    { title: 'ID', dataIndex: 'id', width: 60 },
    { title: '用户名', dataIndex: 'username', width: 140, render: (v, r) => <a onClick={() => openDetail(r)}>{v}</a> },
    { title: '申请说明', dataIndex: 'remark', ellipsis: true, render: v => v || '-' },
    { title: '状态', dataIndex: 'status', width: 100, render: v => <Tag color={RESET_STATUS_COLOR[v] || 'default'}>{RESET_STATUS_LABEL[v] || v}</Tag> },
    { title: '申请时间', dataIndex: 'createTime', width: 170, render: v => v ? new Date(v).toLocaleString() : '-' },
    { title: '处理时间', dataIndex: 'updateTime', width: 170, render: v => v ? new Date(v).toLocaleString() : '-' },
    { title: '操作', width: 90, render: (_, r) => <Button size="small" onClick={() => openDetail(r)}>详情</Button> }
  ]
  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>密码重置申请</Typography.Title>
    <Card styles={{ body: { padding: '8px 20px 16px' } }}>
    <Table rowKey="id" dataSource={resetRequests} columns={prCols} size="small" />
    <Drawer open={!!detail} width={560} onClose={() => setDetail(null)}
      title={detail ? `重置申请详情 - ${detail.username}` : '重置申请详情'}
      footer={detail?.status === 'PENDING' ? <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
        <Button danger onClick={rejectResetRequest}>确认拒绝</Button>
        <Button type="primary" onClick={handleResetRequest}>受理并生成随机密码</Button>
      </div> : <div style={{ textAlign: 'right' }}><Button onClick={() => setDetail(null)}>关闭</Button></div>}>
      {detail && <div>
        <Descriptions column={1} bordered size="small" style={{ marginBottom: 16 }}>
          <Descriptions.Item label="ID">{detail.id}</Descriptions.Item>
          <Descriptions.Item label="用户名">{detail.username}</Descriptions.Item>
          <Descriptions.Item label="状态"><Tag color={RESET_STATUS_COLOR[detail.status] || 'default'}>{RESET_STATUS_LABEL[detail.status] || detail.status}</Tag></Descriptions.Item>
          <Descriptions.Item label="申请说明">{detail.remark || '-'}</Descriptions.Item>
          <Descriptions.Item label="申请时间">{detail.createTime ? new Date(detail.createTime).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="处理时间">{detail.updateTime ? new Date(detail.updateTime).toLocaleString() : '-'}</Descriptions.Item>
          <Descriptions.Item label="处理备注">{detail.adminRemark || '-'}</Descriptions.Item>
        </Descriptions>
        {detail.status === 'PENDING' && <Space direction="vertical" style={{ width: '100%' }} size={16}>
          <div><label style={{ display: 'block', marginBottom: 6 }}>受理备注（可选）</label><Input.TextArea value={handleRemark} onChange={e => setHandleRemark(e.target.value)} placeholder="管理员备注" maxLength={255} rows={2} /></div>
          <div><label style={{ display: 'block', marginBottom: 6 }}>拒绝原因（可选，点击“确认拒绝”时生效）</label><Input.TextArea value={rejectRemark} onChange={e => setRejectRemark(e.target.value)} placeholder="拒绝原因" maxLength={255} rows={2} /></div>
        </Space>}
      </div>}
    </Drawer>
    <Modal open={!!generatedPwd} title="密码重置成功" okText="我已保存" cancelButtonProps={{ style: { display: 'none' } }} onOk={() => setGeneratedPwd('')}>
      <Typography.Paragraph copyable style={{ fontSize: 18, fontWeight: 'bold' }}>{generatedPwd}</Typography.Paragraph>
      <p style={{ color: '#f50' }}>新密码仅此一次展示，请立即复制并告知用户；系统仅保存密码哈希，关闭后无法再次查看。</p>
    </Modal>
    </Card>
  </div>
}
