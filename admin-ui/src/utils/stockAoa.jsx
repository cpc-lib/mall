// 库存 Excel 导入/编辑共用：业务类型、表头、差值展示与 AOA 校验，避免两处实现漂移。
export const BIZ_TYPES = [
  { value: 'MANUAL_ADJUST', label: 'MANUAL_ADJUST（手工调整）' },
  { value: 'ORDER_RESERVE', label: 'ORDER_RESERVE（下单预占）' },
  { value: 'ORDER_COMMIT', label: 'ORDER_COMMIT（支付提交）' },
  { value: 'ORDER_SOLD', label: 'ORDER_SOLD（确认收货结转）' },
  { value: 'ORDER_RELEASE', label: 'ORDER_RELEASE（关单释放）' },
  { value: 'REFUND_RESTOCK', label: 'REFUND_RESTOCK（退款回补）' },
  { value: 'REFUND_LOST', label: 'REFUND_LOST（仅退款货损核销）' }
]

export const STOCK_HEADER = ['商品ID', '商品名称', '调整量']

export const deltaCell = v => v == null || v === 0
  ? <span style={{ color: '#bfbfbf' }}>-</span>
  : <span style={{ color: v > 0 ? '#52c41a' : '#f5222d' }}>{v > 0 ? `+${v}` : v}</span>

export function validateStockAoa(aoa) {
  const items = []
  const issues = []
  aoa.forEach((row, i) => {
    const rawId = row ? row[0] : null
    const rawDelta = row ? row[2] : null
    if (i === 0 && typeof rawId === 'string' && rawId.includes('商品')) return
    if (rawId == null || rawId === '') return
    const line = i + 1
    const productId = Number(rawId)
    if (!Number.isInteger(productId) || productId <= 0) { issues.push(`第${line}行：商品ID ${rawId} 无效`); return }
    if (rawDelta == null || rawDelta === '') return
    const delta = Number(rawDelta)
    if (!Number.isInteger(delta) || delta === 0) { issues.push(`第${line}行：调整量 ${rawDelta} 无效（需非 0 整数）`); return }
    items.push({ productId, delta })
  })
  return { items, issues }
}
