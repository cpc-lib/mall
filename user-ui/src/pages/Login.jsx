import { useState } from 'react'
import { Button, Form, Input, Modal, message } from 'antd'
import { useLocation, useNavigate } from 'react-router-dom'
import authApi from '@/api/auth'
import { saveAuth } from '@/utils/authStore'

export default function Login() {
  const [mode, setMode] = useState('login')
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
      navigate(route.state?.from && !route.state.from.startsWith('/admin') ? route.state.from : '/', { replace: true })
    } finally { setLoading(false) }
  }
  const register = async (values) => {
    setLoading(true)
    try {
      await authApi.register(values)
      message.success('注册成功，请登录')
      setMode('login')
    } finally { setLoading(false) }
  }
  const submitForgot = async () => {
    if (!fpForm.username.trim()) { message.error('请输入用户名'); return }
    try {
      await authApi.submitPasswordResetRequest({ username: fpForm.username.trim(), remark: fpForm.remark.trim() })
      message.success('申请已提交，请联系管理员获取新密码')
      setFpVisible(false); setFpForm({ username: '', remark: '' })
    } catch (e) { /* 错误提示由请求拦截器统一弹出 */ }
  }

  const usernameField = (
    <Form.Item name="username" label="用户名" rules={[{ required: true, message: '请输入用户名' }]}>
      <Input size="large" autoComplete="username" placeholder="请输入用户名" prefix={<span className="tb-auth-ico">👤</span>} />
    </Form.Item>
  )
  const passwordField = (
    <Form.Item name="password" label="密码" rules={[{ required: true, min: 8, message: '密码至少 8 位' }]}>
      <Input.Password size="large" autoComplete={mode === 'login' ? 'current-password' : 'new-password'} placeholder="请输入密码" prefix={<span className="tb-auth-ico">🔒</span>} />
    </Form.Item>
  )

  return <div className="tb-auth">
    <div className="tb-auth-hero">
      <div className="tb-auth-deco tb-auth-deco-a" />
      <div className="tb-auth-deco tb-auth-deco-b" />
      <div className="tb-auth-brand">
        <div className="tb-auth-logo">🛍️</div>
        <div className="tb-auth-name">Mall</div>
      </div>
      <p className="tb-auth-tagline">安全 · 便捷 · 极速的购物体验</p>
    </div>

    <div className="tb-auth-card">
      <div className="tb-auth-switch">
        <button type="button" className={mode === 'login' ? 'on' : ''} onClick={() => setMode('login')}>登 录</button>
        <button type="button" className={mode === 'register' ? 'on' : ''} onClick={() => setMode('register')}>免费注册</button>
      </div>

      {mode === 'login' ? (
        <Form layout="vertical" onFinish={login} requiredMark={false} className="tb-auth-form">
          {usernameField}
          {passwordField}
          <Button htmlType="submit" type="primary" size="large" loading={loading} block className="tb-auth-submit">登 录</Button>
          <div className="tb-auth-forgot">
            <Button type="link" onClick={() => setFpVisible(true)}>忘记密码？</Button>
          </div>
        </Form>
      ) : (
        <Form layout="vertical" onFinish={register} requiredMark={false} className="tb-auth-form">
          {usernameField}
          {passwordField}
          <Button htmlType="submit" type="primary" size="large" loading={loading} block className="tb-auth-submit">注 册</Button>
          <div className="tb-auth-tip">注册即代表同意演示环境使用规范，账号数据仅用于本地演示</div>
        </Form>
      )}
    </div>


    <Modal open={fpVisible} title="找回密码" onOk={submitForgot} onCancel={() => { setFpVisible(false); setFpForm({ username: '', remark: '' }) }} okText="提交申请">
      <p style={{ color: '#999', fontSize: 12 }}>提交后请联系管理员处理，管理员将为你重置新密码。</p>
      <div style={{ marginBottom: 12 }}><Input placeholder="用户名（必填）" value={fpForm.username} onChange={e => setFpForm({ ...fpForm, username: e.target.value })} /></div>
      <Input.TextArea placeholder="补充说明（选填，如注册手机/邮箱等）" maxLength={255} rows={3} value={fpForm.remark} onChange={e => setFpForm({ ...fpForm, remark: e.target.value })} />
    </Modal>
  </div>
}
