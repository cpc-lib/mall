import { useEffect, useState } from 'react'
import { Button, Card, Descriptions, Drawer, Input, Modal, Space, Spin, Table, Tag, message, Typography } from 'antd'
import authApi from '@/api/auth'

export default function UserList() {
  const [records, setRecords] = useState([])
  const [total, setTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [detailId, setDetailId] = useState(null)
  const [detail, setDetail] = useState(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [resetPwd, setResetPwd] = useState('')

  const load = async (p = page, kw = keyword) => {
    setLoading(true)
    try {
      const params = { page: p, size: 10 }
      if (kw.trim()) params.keyword = kw.trim()
      const r = await authApi.adminUsers(params)
      setRecords((r.data && r.data.records) || [])
      setTotal((r.data && r.data.total) || 0)
    } catch (e) { /* 拦截器已提示 */ }
    finally { setLoading(false) }
  }
  useEffect(() => { load() }, [])
  useEffect(() => {
    if (detailId == null) { setDetail(null); return }
    setDetailLoading(true); setDetail(null); setResetPwd('')
    authApi.adminUserDetail(detailId)
      .then(r => setDetail(r.data))
      .catch(() => { /* 拦截器已提示 */ })
      .finally(() => setDetailLoading(false))
  }, [detailId])

  const search = () => { setPage(1); load(1, keyword) }
  const reset = () => { setKeyword(''); setPage(1); load(1, '') }

  const submitReset = async () => {
    if (!resetPwd || resetPwd.length < 8) return message.error('新密码至少 8 位')
    try {
      await authApi.adminResetPassword({ userId: detail.id, newPassword: resetPwd })
      message.success('密码已重置，该用户旧 Token 已失效，登录锁定已解除')
      setResetPwd('')
    } catch (e) { message.error(e?.response?.data?.message || '重置失败') }
  }

  const toggleStatus = (u) => {
    const target = u.userStatus === 'ENABLED' ? 'DISABLED' : 'ENABLED'
    const doSet = async () => {
      try {
        await authApi.setUserStatus(u.id, target)
        message.success(target === 'DISABLED' ? '已禁用，该用户已被强制下线' : '已启用')
        load()
        if (detail && detail.id === u.id) setDetailId(u.id)
      } catch (e) { /* 拦截器已提示 */ }
    }
    if (target === 'DISABLED') {
      Modal.confirm({ title: `禁用用户 - ${u.username}`, content: '禁用后该用户将被强制下线且无法登录，确认操作？', okText: '禁用', okButtonProps: { danger: true }, onOk: doSet })
    } else { doSet() }
  }

  const cols = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '用户名', dataIndex: 'username', width: 160, render: (t, u) => <a onClick={() => setDetailId(u.id)}>{t}</a> },
    { title: '角色', dataIndex: 'role', width: 120, render: v => <Tag color={v === 'ROLE_ADMIN' ? 'red' : 'blue'}>{v}</Tag> },
    { title: '状态', dataIndex: 'userStatus', width: 100, render: v => <Tag color={v === 'ENABLED' ? 'green' : 'red'}>{v === 'ENABLED' ? '正常' : '已禁用'}</Tag> },
    { title: '注册时间', dataIndex: 'createTime', width: 180, render: v => v ? new Date(v).toLocaleString() : '-' },
    {
      title: '操作', width: 200, render: (_, u) => u.role !== 'ROLE_ADMIN' ? (
        <Space>
          <Button size="small" onClick={() => setDetailId(u.id)}>详情/重置密码</Button>
          {u.userStatus === 'ENABLED'
            ? <Button danger size="small" onClick={() => toggleStatus(u)}>禁用</Button>
            : <Button size="small" type="primary" onClick={() => toggleStatus(u)}>启用</Button>}
        </Space>
      ) : <span style={{ color: '#999' }}>-</span>
    }
  ]

  return (
    <div>
      <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>用户列表</Typography.Title>
      <Card>
      <Space style={{ marginBottom: 16 }}>
        <Input placeholder="用户名关键字" allowClear value={keyword} style={{ width: 220 }}
          onChange={e => setKeyword(e.target.value)} onPressEnter={search} />
        <Button type="primary" onClick={search}>查询</Button>
        <Button onClick={reset}>重置</Button>
      </Space>
      <Table rowKey="id" dataSource={records} columns={cols} size="small" loading={loading}
        pagination={{ current: page, pageSize: 10, total, showSizeChanger: false, showTotal: t => `共 ${t} 条`,
          onChange: p => { setPage(p); load(p, keyword) } }} />
      <Drawer open={detailId != null} width={560} onClose={() => setDetailId(null)} title={detail ? `用户详情 - ${detail.username}` : '用户详情'}>
        {detailLoading || !detail ? <Spin style={{ display: 'block', margin: '80px auto' }} /> : (
          <div>
            <Descriptions column={1} bordered>
              <Descriptions.Item label="ID">{detail.id}</Descriptions.Item>
              <Descriptions.Item label="用户名">{detail.username}</Descriptions.Item>
              <Descriptions.Item label="角色"><Tag color={detail.role === 'ROLE_ADMIN' ? 'red' : 'blue'}>{detail.role}</Tag></Descriptions.Item>
              <Descriptions.Item label="状态"><Tag color={detail.userStatus === 'ENABLED' ? 'green' : 'red'}>{detail.userStatus === 'ENABLED' ? '正常' : '已禁用'}</Tag></Descriptions.Item>
              <Descriptions.Item label="在线状态">{detail.online ? <Tag color="green">在线</Tag> : <Tag>离线</Tag>}</Descriptions.Item>
              <Descriptions.Item label="注册时间">{detail.createTime ? new Date(detail.createTime).toLocaleString() : '-'}</Descriptions.Item>
              <Descriptions.Item label="更新时间">{detail.updateTime ? new Date(detail.updateTime).toLocaleString() : '-'}</Descriptions.Item>
            </Descriptions>
            {detail.role !== 'ROLE_ADMIN' && (
              <div style={{ marginTop: 20 }}>
                <h4>重置密码</h4>
                <Space style={{ width: '100%' }}>
                  <Input.Password value={resetPwd} onChange={e => setResetPwd(e.target.value)} placeholder="新密码（至少 8 位）" style={{ width: 280 }} />
                  <Button type="primary" onClick={submitReset}>重置密码</Button>
                </Space>
                <p style={{ fontSize: 12, color: '#8c8c8c', marginTop: 8 }}>重置后该用户旧 Token 全局失效，需重新登录。</p>
              </div>
            )}
          </div>
        )}
      </Drawer>
      </Card>
    </div>
  )
}
