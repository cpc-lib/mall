import { useState } from 'react'
import { Button, Form, Input, Modal, Tabs, message } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import authApi from '@/api/auth'
import { saveAuth } from '@/utils/authStore'

// 淘宝风格登录页：橙红渐变底 + 居中白卡（登录 / 注册 / 忘记密码）
export default function Login() {
  const [loading, setLoading] = useState(false)
  const [fpVisible, setFpVisible] = useState(false)
  const [fpForm, setFpForm] = useState({ username: '', remark: '' })
  const navigate = useNavigate()
  const route = useLocation()
  const login = async (values) => {
    setLoading(true)
    try {
      const r = await authApi.login(values)
      saveAuth(r.data)
      message.success('登录成功')
      // 用户商城工程无管理页面：管理员登录后同样留在商城首页
      navigate(route.state?.from && !route.state.from.startsWith('/admin') ? route.state.from : '/', { replace: true })
    } finally { setLoading(false) }
  }
  const register = async (values) => {
    setLoading(true)
    try { await authApi.register(values); message.success('注册成功，请登录') }
    finally { setLoading(false) }
  }
  const submitForgot = async () => {
    if (!fpForm.username.trim()) { message.error('请输入用户名'); return }
    try {
      await authApi.submitPasswordResetRequest({ username: fpForm.username.trim(), remark: fpForm.remark.trim() })
      message.success('申请已提交，请联系管理员获取新密码')
      setFpVisible(false); setFpForm({ username: '', remark: '' })
    } catch (e) { /* 错误提示由请求拦截器统一弹出 */ }
  }
  const form = (onFinish, button) => <Form layout="vertical" onFinish={onFinish}>
    <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}><Input autoComplete="username" /></Form.Item>
    <Form.Item name="password" label="密码" rules={[{ required: true, min: 8, message: '密码至少 8 位' }]}><Input.Password autoComplete="current-password" /></Form.Item>
    <Button htmlType="submit" type="primary" loading={loading} block style={{ height: 42, fontSize: 15 }}>{button}</Button>
  </Form>
  return <div className="tb-login-wrap">
    <div className="tb-login-card">
      <div className="tb-login-head">淘支付商城 · 欢迎登录</div>
      <div className="tb-login-body">
        <Tabs items={[
          { key: 'login', label: '登 录', children: <div>{form(login, '登录')}<Button type="link" style={{ padding: 0, marginTop: 8 }} onClick={() => setFpVisible(true)}>忘记密码？</Button></div> },
          { key: 'register', label: '免费注册', children: form(register, '注 册') }
        ]} />
      </div>
    </div>
    <Modal open={fpVisible} title="找回密码" onOk={submitForgot} onCancel={() => { setFpVisible(false); setFpForm({ username: '', remark: '' }) }} okText="提交申请">
      <p style={{ color: '#999', fontSize: 12 }}>提交后请联系管理员处理，管理员将为你重置新密码。</p>
      <div style={{ marginBottom: 12 }}><Input placeholder="用户名（必填）" value={fpForm.username} onChange={e => setFpForm({ ...fpForm, username: e.target.value })} /></div>
      <Input.TextArea placeholder="补充说明（选填，如注册手机/邮箱等）" maxLength={255} rows={3} value={fpForm.remark} onChange={e => setFpForm({ ...fpForm, remark: e.target.value })} />
    </Modal>
  </div>
}
