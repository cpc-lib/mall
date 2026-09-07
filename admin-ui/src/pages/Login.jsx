import { useState } from 'react'
import { Button, Card, Form, Input, message } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import authApi from '@/api/auth'
import { clearAuth, saveAuth } from '@/utils/authStore'

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
  return <div style={{ minHeight: '100vh', background: '#e6f4ff', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
    <Card style={{ width: 380, boxShadow: '0 4px 16px rgba(22,119,255,.08)' }} title={<div style={{ textAlign: 'center', fontSize: 16, fontWeight: 600 }}>电商商城平台 · 管理后台</div>}>
      <Form layout="vertical" onFinish={login}>
        <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
          <Input autoComplete="username" placeholder="管理员账号" />
        </Form.Item>
        <Form.Item name="password" label="密码" rules={[{ required: true, message: '请输入密码' }]}>
          <Input.Password autoComplete="current-password" placeholder="密码" />
        </Form.Item>
        <Button htmlType="submit" type="primary" loading={loading} block>登 录</Button>
      </Form>
    </Card>
  </div>
}
