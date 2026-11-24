// Luckysheet 仅在库存 Excel 编辑页使用，按需注入，避免 3.8MB 静态资源阻塞首屏。
const CSS_URLS = [
  '/luckysheet/plugins/css/pluginsCss.css',
  '/luckysheet/plugins/plugins.css',
  '/luckysheet/css/luckysheet.css',
  '/luckysheet/assets/iconfont/iconfont.css'
]

const JS_URLS = [
  '/luckysheet/plugins/js/plugin.js',
  '/luckysheet/luckysheet.umd.js'
]

function injectCss(href) {
  if (document.querySelector(`link[data-luckysheet="${href}"]`)) return
  const link = document.createElement('link')
  link.rel = 'stylesheet'
  link.href = href
  link.dataset.luckysheet = href
  document.head.appendChild(link)
}

function injectScript(src) {
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-luckysheet="${src}"]`)
    if (existing) {
      if (existing.dataset.loaded === 'true') return resolve()
      existing.addEventListener('load', () => resolve())
      existing.addEventListener('error', () => reject(new Error(`Luckysheet 资源加载失败: ${src}`)))
      return
    }
    const script = document.createElement('script')
    script.src = src
    script.dataset.luckysheet = src
    script.onload = () => {
      script.dataset.loaded = 'true'
      resolve()
    }
    script.onerror = () => {
      script.remove()
      reject(new Error(`Luckysheet 资源加载失败: ${src}`))
    }
    document.head.appendChild(script)
  })
}

let loading = null

export function loadLuckysheet() {
  if (window.luckysheet) return Promise.resolve(window.luckysheet)
  if (loading) return loading
  CSS_URLS.forEach(injectCss)
  loading = JS_URLS
    .reduce((p, src) => p.then(() => injectScript(src)), Promise.resolve())
    .then(() => window.luckysheet)
    .catch(err => {
      loading = null
      throw err
    })
  return loading
}
