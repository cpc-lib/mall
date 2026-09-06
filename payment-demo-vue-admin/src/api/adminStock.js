import request from '../utils/request'
export default {
  list: () => request.get('/api/admin/stock/operations'),
  replay: id => request.post(`/api/admin/stock/operations/${id}/replay`),
  products: () => request.get('/api/admin/products'),
  getProduct: id => request.get(`/api/admin/products/${id}`),
  createProduct: (data) => request.post('/api/admin/products', data),
  adjustStock: (id, delta) => request.post(`/api/admin/products/${id}/stock`, { delta }),
  batchAdjustStock: items => request.post('/api/admin/products/stock/batch', { items }),
  transactions: params => request.get('/api/admin/stock/transactions', { params }),
  imports: () => request.get('/api/admin/stock/imports'),
  // 导入 Excel：文件（base64）存后端目录 + 明细暂存 PENDING
  createImport: data => request.post('/api/admin/stock/imports', data),
  // 读取导入记录的 Excel（base64），供前端 JS（Luckysheet）打开编辑
  getImportFile: id => request.get(`/api/admin/stock/imports/${id}/file`),
  // 保存编辑后的 Excel 与明细（仅 PENDING）
  saveImportFile: (id, data) => request.put(`/api/admin/stock/imports/${id}/file`, data),
  // 确认入库：按记录已保存明细执行（无请求体）
  confirmImport: id => request.post(`/api/admin/stock/imports/${id}/confirm`),
  setStatus: (id, productStatus) => request.post(`/api/admin/products/${id}/status`, { productStatus })
}
