import { useEffect, useState } from 'react'
import { Empty, Spin, message } from 'antd'
import { useNavigate } from 'react-router-dom'
import productApi from '@/api/product'
import cartApi from '@/api/cart'
import { getAccessToken, getUser } from '@/utils/authStore'
import { mallImg, onMallImgError } from '@/assets/mallImgs'

// 淘宝风格首页：商品卡片网格
export default function Home() {
  const [loading, setLoading] = useState(true)
  const [products, setProducts] = useState([])
  const navigate = useNavigate()
  const admin = getUser()?.role === 'ROLE_ADMIN'

  useEffect(() => {
    productApi.list().then(r => setProducts(r?.data?.productList || [])).finally(() => setLoading(false))
  }, [])

  const add = async (product) => {
    if (!getAccessToken()) { navigate('/login'); return }
    await cartApi.put({ productId: product.id, quantity: 1, selected: true })
    message.success('已加入购物车')
  }

  return <div className="tb-page">
    <div className="container">
      <div className="tb-section-title">全部商品<span>支付演示商城 · 支付宝 / 微信支付沙箱演示</span></div>
      {loading ? <div className="loading-container"><Spin size="large" /></div>
        : products.length === 0 ? <div className="tb-cardbox"><Empty description="暂无商品" /></div>
        : <div className="tb-grid">
          {products.map(p => {
            const soldOut = p.productStatus !== 'ENABLED' || Number(p.stock || 0) <= 0
            return <div className="tb-card" key={p.id}>
              <div className="tb-card-img"><img src={mallImg(p.id)} alt={p.title} loading="lazy" onError={onMallImgError} /></div>
              <div className="tb-card-body">
                <div className="tb-card-title">{p.title}</div>
                <div className="tb-card-price"><span className="tb-rmb">¥</span>{((p.price || 0) / 100).toFixed(2)}
                  <span className="tb-card-stock">{soldOut ? '缺货/下架' : `库存 ${p.stock}`}</span>
                </div>
                {!admin
                  ? <button className="tb-card-btn" disabled={soldOut} onClick={() => add(p)}>加入购物车</button>
                  : <div className="tb-card-admin">管理员账号不支持下单，请前往管理后台维护商品</div>}
              </div>
            </div>
          })}
        </div>}
    </div>
  </div>
}
