import request from '@/utils/request'

let treePromise = null

// 省市区树为静态数据，模块级缓存 Promise，避免在购物车/地址页间切换时重复请求。
export function getRegionTree() {
  if (!treePromise) {
    treePromise = request.get('/api/regions/tree')
      .then(r => r.data || [])
      .catch(err => {
        treePromise = null
        throw err
      })
  }
  return treePromise
}
