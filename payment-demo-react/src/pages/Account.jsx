import { Button, Form, Input, message } from 'antd'
import { Link } from 'react-router-dom'
import authApi from '@/api/auth'
import { clearAuth, getRefreshToken, getUser } from '@/utils/authStore'
export default function Account(){
  const user = getUser()
  const change = async v => { await authApi.changePassword(v); clearAuth(); message.success('密码修改成功，请重新登录'); location.hash = '#/login' }
  const logout = async () => { try { await authApi.logout(getRefreshToken()) } finally { clearAuth(); location.hash = '#/login' } }
  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">我的</h2>
      <div className="tb-cardbox m-quick">
        <Link className="m-quick-item" to="/orders">📦 我的订单<span className="arrow">›</span></Link>
        <Link className="m-quick-item" to="/refund-applications">💸 我的退款申请<span className="arrow">›</span></Link>
        <Link className="m-quick-item" to="/cart">🛒 购物车<span className="arrow">›</span></Link>
      </div>
      <div className="tb-cardbox" style={{ maxWidth: 520 }}>
        <div className="tb-account-head">
          <div className="tb-account-avatar">{(user?.username || 'U').slice(0, 1).toUpperCase()}</div>
          <div>
            <div className="tb-account-name">{user?.username || '-'}</div>
            <div className="tb-account-role">{user?.role || '-'}</div>
          </div>
        </div>
        <Form layout="vertical" onFinish={change}>
          <Form.Item label="原密码" name="oldPassword" rules={[{ required: true }]}><Input.Password /></Form.Item>
          <Form.Item label="新密码" name="newPassword" rules={[{ required: true, min: 8 }]}><Input.Password /></Form.Item>
          <Button type="primary" htmlType="submit">修改密码</Button> <Button onClick={logout}>退出登录</Button>
        </Form>
      </div>
      <p className="m-demo-note">淘支付商城 · 支付业务演示（支付宝 / 微信支付沙箱）<br/>仅供学习演示，请勿用于真实交易</p>
    </div>
  </div>
}
