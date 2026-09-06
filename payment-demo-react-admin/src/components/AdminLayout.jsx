import { Button, Layout, Menu, Space } from 'antd'
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

// 管理后台独立布局：顶栏 + 左侧功能导航 + 右侧内容（列表/表单），与商城前台 UI 完全隔离
export default function AdminLayout() {
  const nav = useNavigate()
  const { pathname } = useLocation()
  const user = getUser()
  const selected = MENU.find(m => pathname.startsWith(m.key))?.key || '/admin/orders'
  const logout = async () => {
    try { await authApi.logout(getRefreshToken()) } finally { clearAuth(); nav('/login') }
  }
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Layout.Header style={{ background: '#001529', display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '0 24px', boxShadow: '0 2px 8px rgba(0,0,0,.15)', zIndex: 1 }}>
        <span style={{ color: '#fff', fontSize: 16, fontWeight: 600, letterSpacing: 1 }}>支付业务演示 · 管理后台</span>
        <Space>
          <Button type="text" style={{ color: '#fff' }} onClick={() => nav('/')}>前台首页</Button>
          <span style={{ color: 'rgba(255,255,255,.65)' }}>{user?.username || '-'}</span>
          <Button type="text" style={{ color: '#fff' }} onClick={logout}>退出登录</Button>
        </Space>
      </Layout.Header>
      <Layout>
        <Layout.Sider width={200} theme="light" style={{ borderRight: '1px solid #f0f0f0' }}>
          <Menu mode="inline" selectedKeys={[selected]} style={{ borderRight: 0, paddingTop: 8 }}
            items={MENU.map(m => ({ key: m.key, label: m.label }))}
            onClick={({ key }) => nav(key)} />
        </Layout.Sider>
        <Layout.Content style={{ padding: 24, background: '#F5F6F8', overflowX: 'auto', minHeight: 0 }}>
          <Outlet />
        </Layout.Content>
      </Layout>
    </Layout>
  )
}
