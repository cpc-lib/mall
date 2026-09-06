import { useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import authApi from '@/api/auth'
import { clearAuth, saveAuth } from '@/utils/authStore'

// 管理后台专用登录页：仅管理员账号可进入，登录成功直达 /admin/orders
export default function Login() {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const route = useLocation()
  const login = async (values) => {
    setLoading(true)
    try {
      const r = await authApi.login(values)
      if (r.data?.user?.role !== 'ROLE_ADMIN') {
        saveAuth(r.data)
        clearAuth()
        message.error('仅管理员可登录本系统')
        return
      }
      saveAuth(r.data)
      message.success('登录成功')
      navigate(route.state?.from || '/admin/orders', { replace: true })
    } finally { setLoading(false) }
  }
  return <div style={{ minHeight: '100vh', background: '#001529', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
    <Card style={{ width: 380 }} title={<div style={{ textAlign: 'center', fontSize: 16, fontWeight: 600 }}>支付演示 · 管理后台</div>}>
      <Form layout="vertical" onFinish={login}>
        <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input autoComplete="username" placeholder="管理员账号" />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, min: 8, message: '密码至少 8 位' }]}>
          <Input.Password autoComplete="current-password" placeholder="密码（至少 8 位）" />
        </Form.Item>
        <Button htmlType="submit" type="primary" loading={loading} block>登 录</Button>
      </Form>
    </Card>
  </div>
}
