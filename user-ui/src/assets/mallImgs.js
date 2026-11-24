// 演示商品图使用本地内联 SVG，避免离线/内网环境等待外部生图 API 超时。
const PRODUCT_SVGS = [
  ['无线蓝牙耳机', '#e8f4ff', '#4a90e2', '🎧'],
  ['连帽卫衣', '#fff1e8', '#f08c46', '🧥'],
  ['陶瓷马克杯', '#eef7ee', '#5aa469', '☕'],
  ['智能手表', '#f2eefc', '#7c5cd6', '⌚'],
  ['运动跑鞋', '#fff3e0', '#e59a2f', '👟'],
  ['双肩背包', '#eaf4f4', '#3f8f8f', '🎒']
]

const svgDataUri = (label, bg, accent, emoji) => {
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240" viewBox="0 0 240 240">`
    + `<rect width="240" height="240" rx="28" fill="${bg}"/>`
    + `<circle cx="120" cy="104" r="58" fill="${accent}" opacity="0.12"/>`
    + `<text x="120" y="118" font-size="76" text-anchor="middle" dominant-baseline="central">${emoji}</text>`
    + `<text x="120" y="196" font-size="17" text-anchor="middle" fill="${accent}" font-family="PingFang SC,Microsoft YaHei,sans-serif" font-weight="700">${label}</text>`
    + `</svg>`
  return 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(svg)
}

export const MALL_IMGS = PRODUCT_SVGS.map(([label, bg, accent, emoji]) => svgDataUri(label, bg, accent, emoji))
export const mallImg = id => MALL_IMGS[Number(id || 0) % MALL_IMGS.length]

const FALLBACK_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240" viewBox="0 0 240 240"><rect width="240" height="240" rx="28" fill="#f2f3f5"/><text x="120" y="116" font-size="96" text-anchor="middle" dominant-baseline="central">🛍️</text><text x="120" y="198" font-size="17" text-anchor="middle" fill="#fa5416" font-family="PingFang SC,Microsoft YaHei,sans-serif" font-weight="700">演示商品图</text></svg>'
export const MALL_IMG_FALLBACK = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(FALLBACK_SVG)
export const onMallImgError = e => { if (!String(e.currentTarget.src).startsWith('data:')) e.currentTarget.src = MALL_IMG_FALLBACK }
