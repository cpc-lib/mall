import { Button } from 'antd'
import { useNavigate } from 'react-router-dom'

export default function Success() {
  const navigate = useNavigate()

  return (
    <div className="tb-page">
      <div className="container">
        <div className="tb-success">
          <div className="tb-success-icon">
            <svg width="72" height="72" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
              <circle cx="12" cy="12" r="10" />
              <polyline points="16 10 10 16 8 14" />
            </svg>
          </div>
          <h2>支付成功！</h2>
          <p className="tb-success-tip">感谢您的购买，课程已开通</p>
          <div className="tb-success-actions">
            <Button type="primary" onClick={() => navigate('/orders')}>查看订单</Button>
            <Button onClick={() => navigate('/')}>返回首页</Button>
          </div>
        </div>
      </div>
    </div>
  )
}
