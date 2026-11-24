import { useEffect, useState } from 'react'
import { Empty, Pagination, Spin, message } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import productApi from '@/api/product'
import cartApi from '@/api/cart'
import { getAccessToken, getUser } from '@/utils/authStore'
import { mallImg, onMallImgError } from '@/assets/mallImgs'

export default function Home() {
  const [loading, setLoading] = useState(true)
  const [products, setProducts] = useState([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const size = 12
  const navigate = useNavigate()
  const admin = getUser()?.role === 'ROLE_ADMIN'
  const [searchParams] = useSearchParams()
  const q = (searchParams.get('q') || '').trim()
  const searching = Boolean(q)

  const load = async (nextPage = 1) => {
    setLoading(true)
    try {
      const r = await productApi.page({ page: searching ? 1 : nextPage, size: searching ? 100 : size })
      const data = r?.data || {}
      const records = data.records || []
      setProducts(searching
        ? records.filter(p => String(p.title || '').toLowerCase().includes(q.toLowerCase()))
        : records)
      setTotal(searching ? 0 : (data.total || 0))
      setPage(data.page || nextPage)
    } finally { setLoading(false) }
  }
  useEffect(() => { load(1) }, [q]) // eslint-disable-line react-hooks/exhaustive-deps

  const add = async (product) => {
    if (!getAccessToken()) { navigate('/login'); return }
    await cartApi.put({ productId: product.id, quantity: 1, selected: true })
    message.success('已加入购物车')
  }

  return <div className="tb-page">
    <div className="container">
      <div className="tb-section-title">{searching ? `搜索“${q}”` : '全部商品'}</div>
      {loading ? <div className="loading-container"><Spin size="large" /></div>
        : products.length === 0 ? <div className="tb-cardbox"><Empty description={searching ? '未找到相关商品' : '暂无商品'} /></div>
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
      {total > size && <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
        <Pagination current={page} pageSize={size} total={total} showSizeChanger={false} onChange={p => load(p)} />
      </div>}
    </div>
  </div>
}
