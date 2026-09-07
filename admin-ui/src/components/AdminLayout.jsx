import { useState } from 'react'
import { Button, Form, Input, Layout, Menu, message, Modal, Space } from 'antd'
import { Outlet, useLocation, useNavigate } from 'react-router-dom'
import authApi from '@/api/auth'
import { clearAuth, getRefreshToken, getUser } from '@/utils/authStore'

const MENU = [
  { key: '/admin/orders', label: '订单管理' },
  { key: '/admin/shipping', label: '订单发货' },
  { key: '/admin/products', label: '商品库存' },
  { key: '/admin/refunds', label: '退款受理' },
  { key: '/admin/mq-logs', label: 'MQ/库存异常' },
  { key: '/admin/users', label: '用户列表' },
  { key: '/admin/reset-requests', label: '密码重置申请' },
  { key: '/admin/reset-password', label: '重置用户密码' },
  { key: '/admin/stock-maintenance', label: '批量库存维护' },
  { key: '/admin/download', label: '下载账单' },
  { key: '/admin/payment-config', label: '支付配置' },
  { key: '/admin/reconciliation', label: '对账管理' }
]

export default function AdminLayout() {
  const nav = useNavigate()
  const { pathname } = useLocation()
  const user = getUser()
  const selected = MENU.find(m => pathname.startsWith(m.key))?.key || '/admin/orders'
  const logout = async () => {
    try { await authApi.logout(getRefreshToken()) } finally { clearAuth(); nav('/login') }
  }
  const [pwdOpen, setPwdOpen] = useState(false)
  const [pwdForm] = Form.useForm()
  const openPwdModal = () => { pwdForm.resetFields(); setPwdOpen(true) }
  const changePassword = async v => {
    await authApi.changePassword({ oldPassword: v.oldPassword, newPassword: v.newPassword })
    setPwdOpen(false)
    clearAuth()
    message.success('密码修改成功，请重新登录')
    nav('/login')
  }
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Header style={{ background: '#fff', borderBottom: '1px solid #f0f0f0', boxShadow: '0 2px 8px rgba(0,0,0,.03)', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', zIndex: 1 }}>
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: 10, fontSize: 16, fontWeight: 600, color: '#262626', letterSpacing: 1 }}>
          <span style={{ width: 10, height: 10, borderRadius: 3, background: '#1677ff', display: 'inline-block' }} />
          电商商城平台 · 管理后台
        </span>
        <Space>
          <Button type="text" onClick={() => nav('/')}>前台首页</Button>
          <span style={{ color: 'rgba(0,0,0,.65)' }}>{user?.username || '-'}</span>
          <Button type="text" onClick={openPwdModal}>修改密码</Button>
          <Button type="text" onClick={logout}>退出登录</Button>
        </Space>
      </Layout.Header>
      <Layout>
        <Layout.Sider width={200} theme="light" style={{ background: '#fff', borderRight: '1px solid #f0f0f0' }}>
          <Menu mode="inline" selectedKeys={[selected]} style={{ borderRight: 0, paddingTop: 8 }}
            items={MENU.map(m => ({ key: m.key, label: m.label }))}
            onClick={({ key }) => nav(key)} />
        </Layout.Sider>
        <Layout.Content style={{ padding: 24, background: '#e6f4ff', overflowX: 'auto', minHeight: 0 }}>
          <Outlet />
        </Layout.Content>
      </Layout>
      <Modal title="修改密码" open={pwdOpen} onCancel={() => setPwdOpen(false)} onOk={() => pwdForm.submit()} okText="确认修改" cancelText="取消">
        <Form form={pwdForm} layout="vertical" onFinish={changePassword}>
          <Form.Item label="原密码" name="oldPassword" rules={[{ required: true, message: '请输入原密码' }]}>
            <Input.Password autoComplete="current-password" placeholder="请输入原密码" />
          </Form.Item>
          <Form.Item label="新密码" name="newPassword" rules={[{ required: true, min: 8, max: 64, message: '新密码为 8-64 位' }]}>
            <Input.Password autoComplete="new-password" placeholder="新密码（8-64 位）" />
          </Form.Item>
          <Form.Item label="确认新密码" name="confirmPassword" dependencies={['newPassword']} rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator: (_, v) => (!v || v === getFieldValue('newPassword')) ? Promise.resolve() : Promise.reject(new Error('两次输入的密码不一致'))
            })
          ]}>
            <Input.Password autoComplete="new-password" placeholder="再次输入新密码" />
          </Form.Item>
        </Form>
      </Modal>
    </Layout>
  )
}
