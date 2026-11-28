import { useEffect, useMemo, useState } from 'react'
import { Button, Descriptions, Empty, InputNumber, Modal, Pagination, Space, Spin, Tag, message } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import productApi from '@/api/product'
import cartApi from '@/api/cart'
import { getAccessToken, getUser } from '@/utils/authStore'
import { mallImg, onMallImgError } from '@/assets/mallImgs'
import {
  findMatchingSku,
  formatFen,
  getDefaultSelection,
  isSpecOptionPurchasable
} from '@/utils/skuSpec'

export default function Home() {
  const [loading, setLoading] = useState(true)
  const [spus, setSpus] = useState([])
  const [page, setPage] = useState(1)
  const [total, setTotal] = useState(0)
  const size = 12
  const navigate = useNavigate()
  const admin = getUser()?.role === 'ROLE_ADMIN'
  const [searchParams] = useSearchParams()
  const q = (searchParams.get('q') || '').trim()
  const searching = Boolean(q)

  const [detailOpen, setDetailOpen] = useState(false)
  const [detailLoading, setDetailLoading] = useState(false)
  const [detail, setDetail] = useState(null)
  const [selectedSpecs, setSelectedSpecs] = useState({})
  const [buyQty, setBuyQty] = useState(1)
  const [adding, setAdding] = useState(false)

  const load = async (nextPage = 1) => {
    setLoading(true)
    try {
      const r = await productApi.spuPage({
        page: nextPage,
        size,
        keyword: searching ? q : undefined
      })
      const data = r?.data || {}
      setSpus(data.records || [])
      setTotal(data.total || 0)
      setPage(data.page || nextPage)
    } finally { setLoading(false) }
  }
  useEffect(() => { load(1) }, [q]) // eslint-disable-line react-hooks/exhaustive-deps

  const openDetail = async (spuId) => {
    setDetailOpen(true)
    setDetailLoading(true)
    setDetail(null)
    setSelectedSpecs({})
    setBuyQty(1)
    try {
      const r = await productApi.spuDetail(spuId)
      const d = r?.data || null
      setDetail(d)
      if (d) {
        setSelectedSpecs(getDefaultSelection(d.specGroups, d.skus, d.spu?.defaultSkuId))
      }
    } finally { setDetailLoading(false) }
  }

  const matchedSku = useMemo(() => {
    if (!detail) return null
    return findMatchingSku(detail.skus || [], selectedSpecs, detail.specGroups || [])
  }, [detail, selectedSpecs])

  const addSkuToCart = async (skuId, quantity = 1) => {
    if (!getAccessToken()) { navigate('/login'); return }
    if (!skuId) { message.warning('请先选择完整规格'); return }
    setAdding(true)
    try {
      await cartApi.put({ productId: skuId, quantity, selected: true })
      message.success('已加入购物车')
      setDetailOpen(false)
    } finally { setAdding(false) }
  }

  const spuEnabled = detail?.spu?.spuStatus === 'ENABLED'
  const skuPurchasable = Boolean(
    spuEnabled && matchedSku && matchedSku.productStatus === 'ENABLED' && Number(matchedSku.stock || 0) > 0
  )

  return <div className="tb-page">
    <div className="container">
      <div className="tb-section-title">{searching ? `搜索“${q}”` : '全部商品'}</div>
      {loading ? <div className="loading-container"><Spin size="large" /></div>
        : spus.length === 0 ? <div className="tb-cardbox"><Empty description={searching ? '未找到相关商品' : '暂无商品'} /></div>
        : <div className="tb-grid">
          {spus.map(spu => {
            const soldOut = spu.spuStatus !== 'ENABLED' || Number(spu.totalAvailableStock || 0) <= 0
            const isMulti = spu.specMode === 'MULTI'
            const hasRange = Number(spu.maxPrice || 0) > Number(spu.minPrice || 0)
            return <div className="tb-card" key={spu.spuId}>
              <div className="tb-card-img" style={{ cursor: 'pointer' }} onClick={() => openDetail(spu.spuId)}>
                <img src={spu.mainImage || mallImg(spu.defaultSkuId || spu.spuId)} alt={spu.title} loading="lazy" onError={onMallImgError} />
              </div>
              <div className="tb-card-body">
                <div className="tb-card-title" style={{ cursor: 'pointer' }} onClick={() => openDetail(spu.spuId)}>
                  {spu.title}
                </div>
                <div style={{ margin: '4px 0', minHeight: 22, display: 'flex', flexWrap: 'wrap', gap: 4 }}>
                  {isMulti && <Tag color="blue" style={{ margin: 0 }}>多规格 ({spu.skuCount})</Tag>}
                  {spu.brandName && <Tag style={{ margin: 0 }}>{spu.brandName}</Tag>}
                  {spu.categoryName && <Tag style={{ margin: 0 }}>{spu.categoryName}</Tag>}
                </div>
                {Array.isArray(spu.attrSummary) && spu.attrSummary.length > 0 && (
                  <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 6, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    {spu.attrSummary.slice(0, 2).join(' · ')}
                  </div>
                )}
                <div className="tb-card-price">
                  <span className="tb-rmb">¥</span>{formatFen(spu.minPrice)}
                  {hasRange && <span style={{ fontSize: 12, marginLeft: 2, fontWeight: 400 }}>起</span>}
                  <span className="tb-card-stock">{soldOut ? '缺货/下架' : `库存 ${spu.totalAvailableStock}`}</span>
                </div>
                {!admin ? (
                  isMulti ? (
                    <button className="tb-card-btn" onClick={() => openDetail(spu.spuId)}>{soldOut ? '查看详情' : '选择规格'}</button>
                  ) : (
                    <div style={{ display: 'flex', gap: 6 }}>
                      <button className="tb-card-btn" style={{ flex: 1, background: '#f5f5f5', color: '#333', border: '1px solid #d9d9d9' }} onClick={() => openDetail(spu.spuId)}>参数详情</button>
                      <button className="tb-card-btn" style={{ flex: 1 }} disabled={soldOut} onClick={() => addSkuToCart(spu.defaultSkuId, 1)}>加入购物车</button>
                    </div>
                  )
                ) : (
                  <div className="tb-card-admin">管理员账号不支持下单，请前往管理后台维护商品</div>
                )}
              </div>
            </div>
          })}
        </div>}
      {total > size && <div style={{ display: 'flex', justifyContent: 'center', marginTop: 16 }}>
        <Pagination current={page} pageSize={size} total={total} showSizeChanger={false} onChange={p => load(p)} />
      </div>}
    </div>

    <Modal
      open={detailOpen}
      onCancel={() => setDetailOpen(false)}
      title={detail?.spu?.title || '商品详情'}
      width={440}
      style={{ maxWidth: 'calc(100vw - 24px)' }}
      footer={null}
    >
      {detailLoading || !detail ? (
        <div style={{ textAlign: 'center', padding: '48px 0' }}><Spin /></div>
      ) : (
        <div>
          <Space wrap style={{ marginBottom: 8 }}>
            {detail.spu.brandName && <Tag color="geekblue">{detail.spu.brandName}</Tag>}
            {detail.spu.categoryName && <Tag>{detail.spu.categoryName}</Tag>}
            <Tag color={detail.spu.specMode === 'MULTI' ? 'blue' : 'default'}>
              {detail.spu.specMode === 'MULTI' ? '多规格' : '单规格'}
            </Tag>
            {!spuEnabled && <Tag color="red">商品已下架</Tag>}
          </Space>
          {detail.spu.subtitle && <div style={{ color: '#595959', marginBottom: 8 }}>{detail.spu.subtitle}</div>}
          {detail.spu.description && <div style={{ color: '#8c8c8c', fontSize: 13, marginBottom: 12 }}>{detail.spu.description}</div>}

          <div style={{ background: '#fafafa', padding: 12, borderRadius: 8, marginBottom: 16 }}>
            <div style={{ fontSize: 22, fontWeight: 700, color: '#ff4d4f' }}>
              ¥{matchedSku ? formatFen(matchedSku.price) : formatFen(detail.spu.minPrice)}
              {!matchedSku && Number(detail.spu.maxPrice || 0) > Number(detail.spu.minPrice || 0) && (
                <span style={{ fontSize: 14, fontWeight: 400, marginLeft: 4 }}>- ¥{formatFen(detail.spu.maxPrice)}</span>
              )}
            </div>
            <div style={{ fontSize: 13, color: '#595959', marginTop: 4 }}>
              {matchedSku ? (
                <>
                  {matchedSku.specText && <span style={{ marginRight: 12 }}>已选：<b>{matchedSku.specText}</b></span>}
                  <span>可用库存：<b>{matchedSku.stock}</b></span>
                  {matchedSku.productStatus !== 'ENABLED' && <Tag color="red" style={{ marginLeft: 8 }}>该规格已下架</Tag>}
                </>
              ) : (
                <span>请选择完整的销售规格组合</span>
              )}
            </div>
          </div>

          {Array.isArray(detail.specGroups) && detail.specGroups.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              {detail.specGroups.map(group => (
                <div key={group.id} style={{ marginBottom: 10 }}>
                  <div style={{ fontWeight: 600, marginBottom: 6 }}>{group.specName}</div>
                  <Space wrap>
                    {(group.values || []).map(val => {
                      const active = String(selectedSpecs[String(group.id)] || '') === String(val.id)
                      const purchasable = isSpecOptionPurchasable(detail.skus || [], selectedSpecs, group.id, val.id)
                      return (
                        <Button
                          key={val.id}
                          type={active ? 'primary' : 'default'}
                          size="small"
                          style={!active && !purchasable ? { color: '#bfbfbf', borderStyle: 'dashed' } : undefined}
                          onClick={() => setSelectedSpecs(prev => ({ ...prev, [String(group.id)]: String(val.id) }))}
                        >
                          {val.specValue}{!purchasable ? '（无货）' : ''}
                        </Button>
                      )
                    })}
                  </Space>
                </div>
              ))}
            </div>
          )}

          {Array.isArray(detail.attrs) && detail.attrs.length > 0 && (
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontWeight: 600, marginBottom: 6 }}>商品参数</div>
              <Descriptions size="small" column={1} bordered>
                {detail.attrs.map(a => (
                  <Descriptions.Item key={a.id || a.attrName} label={a.attrName}>{a.attrValue}</Descriptions.Item>
                ))}
              </Descriptions>
            </div>
          )}

          {!admin && (
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginTop: 16 }}>
              <Space>
                <span>购买数量：</span>
                <InputNumber
                  min={1}
                  max={Math.max(1, Number(matchedSku?.stock || 1))}
                  value={buyQty}
                  onChange={v => setBuyQty(Number(v || 1))}
                />
              </Space>
              <Button
                type="primary"
                loading={adding}
                disabled={!skuPurchasable}
                onClick={() => addSkuToCart(matchedSku?.id, buyQty)}
              >
                {skuPurchasable ? '加入购物车' : '当前规格不可售'}
              </Button>
            </div>
          )}
        </div>
      )}
    </Modal>
  </div>
}
