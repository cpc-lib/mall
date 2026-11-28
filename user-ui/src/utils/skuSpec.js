/**
 * SPU / SKU 规格匹配与价格展示纯逻辑工具（可供 node --test 单测）。
 */

export function parseSpecKey(specKey) {
  if (!specKey || specKey === 'DEFAULT') return {}
  const map = {}
  for (const seg of String(specKey).split(';')) {
    const [g, v] = seg.split(':')
    if (g && v) map[String(g)] = String(v)
  }
  return map
}

export function buildSpecKeyFromSelection(selectedMap) {
  if (!selectedMap) return 'DEFAULT'
  const entries = Object.entries(selectedMap).filter(([, v]) => v != null && v !== '')
  if (entries.length === 0) return 'DEFAULT'
  entries.sort((a, b) => Number(a[0]) - Number(b[0]))
  return entries.map(([g, v]) => `${g}:${v}`).join(';')
}

export function findMatchingSku(skus = [], selectedMap = {}, specGroups = []) {
  if (!Array.isArray(skus) || skus.length === 0) return null
  if (!Array.isArray(specGroups) || specGroups.length === 0) {
    return skus.find(s => s.isDefault) || skus[0]
  }
  if (Object.keys(selectedMap || {}).length < specGroups.length) {
    return null
  }
  const targetKey = buildSpecKeyFromSelection(selectedMap)
  return skus.find(s => s.specKey === targetKey) || null
}

export function isSpecOptionPurchasable(skus = [], selectedMap = {}, groupId, valueId) {
  const gKey = String(groupId)
  const vKey = String(valueId)
  return skus.some(sku => {
    if (sku.productStatus !== 'ENABLED' || Number(sku.stock || 0) <= 0) return false
    const parsed = parseSpecKey(sku.specKey)
    if (parsed[gKey] !== vKey) return false
    for (const [otherG, otherV] of Object.entries(selectedMap || {})) {
      if (String(otherG) === gKey || otherV == null || otherV === '') continue
      if (parsed[String(otherG)] !== String(otherV)) return false
    }
    return true
  })
}

export function getDefaultSelection(specGroups = [], skus = [], defaultSkuId = null) {
  if (!Array.isArray(specGroups) || specGroups.length === 0 || !Array.isArray(skus) || skus.length === 0) {
    return {}
  }
  const candidate =
    skus.find(s => defaultSkuId != null && s.id === defaultSkuId && s.productStatus === 'ENABLED' && Number(s.stock || 0) > 0) ||
    skus.find(s => s.productStatus === 'ENABLED' && Number(s.stock || 0) > 0) ||
    skus.find(s => defaultSkuId != null && s.id === defaultSkuId) ||
    skus[0]
  return parseSpecKey(candidate?.specKey)
}

export function formatFen(fen) {
  return ((Number(fen || 0)) / 100).toFixed(2)
}

export function formatPriceRange(minPrice, maxPrice) {
  const min = Number(minPrice || 0)
  const max = Number(maxPrice || min)
  if (max > min) {
    return `¥${formatFen(min)} - ¥${formatFen(max)}`
  }
  return `¥${formatFen(min)}`
}
