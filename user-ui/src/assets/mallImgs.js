const P = 'https://trae-api-cn.mchost.guru/api/ide/v1/text_to_image?image_size=square&prompt='
export const MALL_IMGS = [
  P + 'wireless%20bluetooth%20earbuds%20product%20photo%20on%20clean%20white%20background%2C%20e-commerce%20product%20photography%2C%20bright%2C%20high%20quality',
  P + 'stylish%20hoodie%20product%20photo%20on%20clean%20white%20background%2C%20e-commerce%20product%20photography%2C%20bright%2C%20high%20quality',
  P + 'ceramic%20coffee%20mug%20product%20photo%20on%20clean%20white%20background%2C%20e-commerce%20product%20photography%2C%20bright%2C%20high%20quality',
  P + 'smart%20watch%20product%20photo%20on%20clean%20white%20background%2C%20e-commerce%20product%20photography%2C%20bright%2C%20high%20quality',
  P + 'running%20sneakers%20product%20photo%20on%20clean%20white%20background%2C%20e-commerce%20product%20photography%2C%20bright%2C%20high%20quality',
  P + 'casual%20backpack%20product%20photo%20on%20clean%20white%20background%2C%20e-commerce%20product%20photography%2C%20bright%2C%20high%20quality'
]
export const mallImg = id => MALL_IMGS[Number(id || 0) % MALL_IMGS.length]

const FALLBACK_SVG = '<svg xmlns="http://www.w3.org/2000/svg" width="240" height="240" viewBox="0 0 240 240"><rect width="240" height="240" rx="28" fill="#f2f3f5"/><text x="120" y="116" font-size="96" text-anchor="middle" dominant-baseline="central">🛍️</text><text x="120" y="198" font-size="17" text-anchor="middle" fill="#fa5416" font-family="PingFang SC,Microsoft YaHei,sans-serif" font-weight="700">演示商品图</text></svg>'
export const MALL_IMG_FALLBACK = 'data:image/svg+xml;charset=utf-8,' + encodeURIComponent(FALLBACK_SVG)
export const onMallImgError = e => { if (!String(e.currentTarget.src).startsWith('data:')) e.currentTarget.src = MALL_IMG_FALLBACK }
