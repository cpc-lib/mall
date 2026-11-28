/**
 * 管理端多规格笛卡尔积 SKU 矩阵生成与已有 SKU 保留工具（可供 node --test 单测）。
 */

export function parseSpecValuesInput(text) {
  if (!text) return []
  const seen = new Set()
  const list = []
  for (const raw of String(text).split(/[,，\n]/)) {
    const v = raw.trim()
    if (v && !seen.has(v)) {
      seen.add(v)
      list.push(v)
    }
  }
  return list
}

export function specPairsSignature(specs = []) {
  return [...specs]
    .sort((a, b) => String(a.specName).localeCompare(String(b.specName)))
    .map(p => `${p.specName}=${p.specValue}`)
    .join('|')
}

export function buildSkuMatrix(specGroups = [], existingSkus = []) {
  const validGroups = specGroups
    .map(g => ({
      specName: String(g.specName || '').trim(),
      values: Array.isArray(g.values) ? g.values.filter(Boolean) : parseSpecValuesInput(g.valuesText)
    }))
    .filter(g => g.specName && g.values.length > 0)

  if (validGroups.length === 0) return []

  const existingBySig = new Map()
  for (const sku of existingSkus || []) {
    if (Array.isArray(sku.specs) && sku.specs.length > 0) {
      existingBySig.set(specPairsSignature(sku.specs), sku)
    }
  }

  let combos = [[]]
  for (const group of validGroups) {
    const next = []
    for (const prefix of combos) {
      for (const val of group.values) {
        next.push([...prefix, { specName: group.specName, specValue: val }])
      }
    }
    combos = next
  }

  return combos.map(specs => {
    const sig = specPairsSignature(specs)
    const prev = existingBySig.get(sig)
    const specText = specs.map(s => s.specValue).join(' / ')
    if (prev) {
      return {
        ...prev,
        specs,
        specText
      }
    }
    return {
      skuId: null,
      skuCode: '',
      price: 100,
      stock: 50,
      productStatus: 'ENABLED',
      specs,
      specText
    }
  })
}
