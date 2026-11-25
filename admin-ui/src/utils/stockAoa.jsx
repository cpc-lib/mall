// 给 React 页面用的入口：重新导出纯逻辑 + 提供 JSX 版差值单元格渲染。
import { BIZ_TYPES as _BIZ_TYPES, STOCK_HEADER as _STOCK_HEADER, validateStockAoa as _validateStockAoa } from './stockAoaCore.js'

export const BIZ_TYPES = _BIZ_TYPES
export const STOCK_HEADER = _STOCK_HEADER
export const validateStockAoa = _validateStockAoa

// 差值单元格渲染器（JSX）。
export function deltaCell(v) {
  if (v == null || v === 0) return <span style={{ color: '#bfbfbf' }}>-</span>
  const text = v > 0 ? `+${v}` : String(v)
  return <span style={{ color: v > 0 ? '#52c41a' : '#f5222d' }}>{text}</span>
}
