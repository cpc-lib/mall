import { useState } from 'react'
import { Form, Input, message, Modal } from 'antd'
import { Link } from 'react-router-dom'
import authApi from '@/api/auth'
import { clearAuth, getRefreshToken } from '@/utils/authStore'
import UiIcon from '@/components/UiIcon.jsx'

export default function Account(){
  const [pwdOpen, setPwdOpen] = useState(false)
  const [form] = Form.useForm()
  const openPwd = () => { form.resetFields(); setPwdOpen(true) }
  const change = async v => {
    await authApi.changePassword({ oldPassword: v.oldPassword, newPassword: v.newPassword })
    setPwdOpen(false)
    clearAuth()
    message.success('密码修改成功，请重新登录')
    location.hash = '#/login'
  }
  const doLogout = async () => { try { await authApi.logout(getRefreshToken()) } finally { clearAuth(); location.hash = '#/login' } }
  const confirmLogout = () => Modal.confirm({
    title: '退出登录',
    content: '是否确认登出？',
    okText: '确认',
    cancelText: '取消',
    onOk: doLogout
  })
  return <div className="tb-page">
    <div className="container">
      <h2 className="tb-h2">我的</h2>
      <div className="tb-cardbox m-quick">
        <Link className="m-quick-item" to="/orders"><span className="m-quick-main"><span className="m-quick-icon"><UiIcon name="box" size={20} /></span>我的订单</span><UiIcon name="chevron" size={17} className="arrow" /></Link>
        <Link className="m-quick-item" to="/refund-applications"><span className="m-quick-main"><span className="m-quick-icon"><UiIcon name="refund" size={20} /></span>我的退款申请</span><UiIcon name="chevron" size={17} className="arrow" /></Link>
        <Link className="m-quick-item" to="/cart"><span className="m-quick-main"><span className="m-quick-icon"><UiIcon name="cart" size={20} /></span>购物车</span><UiIcon name="chevron" size={17} className="arrow" /></Link>
        <Link className="m-quick-item" to="/addresses"><span className="m-quick-main"><span className="m-quick-icon"><UiIcon name="pin" size={20} /></span>收货地址</span><UiIcon name="chevron" size={17} className="arrow" /></Link>
        <button type="button" className="m-quick-item" onClick={openPwd}><span className="m-quick-main"><span className="m-quick-icon"><UiIcon name="key" size={20} /></span>修改密码</span><UiIcon name="chevron" size={17} className="arrow" /></button>
        <button type="button" className="m-quick-item m-quick-danger" onClick={confirmLogout}><span className="m-quick-main"><span className="m-quick-icon"><UiIcon name="logout" size={20} /></span>退出登录</span><UiIcon name="chevron" size={17} className="arrow" /></button>
      </div>
    </div>
    <Modal
      title="修改密码"
      open={pwdOpen}
      onCancel={() => setPwdOpen(false)}
      onOk={() => form.submit()}
      okText="确认修改"
      cancelText="取消"
      destroyOnHidden
      maskClosable={false}
    >
      <Form form={form} layout="vertical" onFinish={change}>
        <Form.Item label="原密码" name="oldPassword" rules={[{ required: true, message: '请输入原密码' }]}>
          <Input.Password placeholder="请输入原密码" />
        </Form.Item>
        <Form.Item label="新密码" name="newPassword" rules={[{ required: true, min: 8, message: '新密码至少8位' }]}>
          <Input.Password placeholder="请输入新密码（至少8位）" />
        </Form.Item>
        <Form.Item
          label="确认新密码"
          name="confirmPassword"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: '请再次输入新密码' },
            ({ getFieldValue }) => ({
              validator(_, v) {
                return !v || v === getFieldValue('newPassword')
                  ? Promise.resolve()
                  : Promise.reject(new Error('两次输入的新密码不一致'))
              }
            })
          ]}
        >
          <Input.Password placeholder="请再次输入新密码" />
        </Form.Item>
      </Form>
    </Modal>
  </div>
}
