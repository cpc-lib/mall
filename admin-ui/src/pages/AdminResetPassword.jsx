import { useEffect, useState } from 'react'
import { Button, Card, Input, Space, Table, Tag, message, Typography } from 'antd'
import authApi from '@/api/auth'
import { ROLE_COLOR, USER_STATUS_COLOR, USER_STATUS_LABEL } from '@/utils/statusLabels'

export default function AdminResetPassword() {
  const [records, setRecords] = useState([])
  const [total, setTotal] = useState(0)
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const [loading, setLoading] = useState(false)
  const [selected, setSelected] = useState(null)
  const [pwd, setPwd] = useState('')

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
  useEffect(() => { load(1, '') }, [])

  const search = () => { setPage(1); load(1, keyword) }
  const resetFilter = () => { setKeyword(''); setPage(1); load(1, '') }

  const submitReset = async () => {
    if (!selected || pwd.length < 8) return
    try {
      await authApi.adminResetPassword({ userId: selected.id, newPassword: pwd })
      message.success('密码已重置，目标用户旧 Token 已全局失效')
      setPwd('')
    } catch (e) { /* 拦截器已提示 */ }
  }

  const cols = [
    { title: 'ID', dataIndex: 'id', width: 70 },
    { title: '用户名', dataIndex: 'username', width: 160 },
    { title: '角色', dataIndex: 'role', width: 120, render: v => <Tag color={ROLE_COLOR[v] || 'blue'}>{v}</Tag> },
    { title: '状态', dataIndex: 'userStatus', width: 100, render: v => <Tag color={USER_STATUS_COLOR[v] || 'red'}>{USER_STATUS_LABEL[v] || v}</Tag> },
    { title: '注册时间', dataIndex: 'createTime', width: 180, render: v => v ? new Date(v).toLocaleString() : '-' },
    {
      title: '说明', render: (_, u) => u.role === 'ROLE_ADMIN'
        ? <span style={{ color: '#999', fontSize: 12 }}>管理员账号不可重置密码</span>
        : selected?.id === u.id ? <span style={{ color: '#67c23a', fontSize: 12 }}>已选择该用户</span> : null
    }
  ]

  return <div>
    <Typography.Title level={4} style={{ marginTop: 0, marginBottom: 16 }}>重置用户密码</Typography.Title>
    <Card>
      <Space style={{ marginBottom: 16 }}>
        <Input placeholder="用户名关键字" allowClear value={keyword} style={{ width: 220 }}
          onChange={e => setKeyword(e.target.value)} onPressEnter={search} />
        <Button type="primary" onClick={search}>查询</Button>
        <Button onClick={resetFilter}>重置</Button>
      </Space>
      <Table rowKey="id" dataSource={records} columns={cols} size="small" loading={loading}
        rowSelection={{
          type: 'radio',
          selectedRowKeys: selected ? [selected.id] : [],
          getCheckboxProps: u => ({ disabled: u.role === 'ROLE_ADMIN' }),
          onChange: (keys, rows) => setSelected(rows[0] || null)
        }}
        pagination={{ current: page, pageSize: 10, total, showSizeChanger: false, showTotal: t => `共 ${t} 条`,
          onChange: p => { setPage(p); load(p, keyword) } }} />
    </Card>
    <Card style={{ marginTop: 16, maxWidth: 720 }}>
      <div style={{ marginBottom: 12 }}>
        已选用户：{selected
          ? <span><strong>{selected.username}</strong>（ID: {selected.id}）</span>
          : <span style={{ color: '#999' }}>请先在上方列表中勾选一个普通用户</span>}
      </div>
      <Space>
        <Input.Password placeholder="新密码（至少8位）" value={pwd} disabled={!selected}
          onChange={e => setPwd(e.target.value)} style={{ width: 260 }} />
        <Button type="primary" disabled={!selected || pwd.length < 8} onClick={submitReset}>重置密码</Button>
      </Space>
      <p style={{ margin: '12px 0 0', fontSize: 12, color: '#8c8c8c' }}>重置后目标用户旧 Token 将全局失效，需重新登录。</p>
    </Card>
  </div>
}
