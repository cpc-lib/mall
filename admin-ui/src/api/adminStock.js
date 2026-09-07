import request from '@/utils/request'
export default {
  products: () => request.get('/api/admin/products'),
  getProduct: (id) => request.get(`/api/admin/products/${id}`),
  createProduct: (data) => request.post('/api/admin/products', data),
  adjustStock: (id, delta) => request.post(`/api/admin/products/${id}/stock`, { delta }),
  batchAdjustStock: (items) => request.post('/api/admin/products/stock/batch', { items }),
  transactions: (params) => request.get('/api/admin/stock/transactions', { params }),
  imports: () => request.get('/api/admin/stock/imports'),
  createImport: (data) => request.post('/api/admin/stock/imports', data),
  getImportFile: (id) => request.get(`/api/admin/stock/imports/${id}/file`),
  saveImportFile: (id, data) => request.put(`/api/admin/stock/imports/${id}/file`, data),
  confirmImport: (id) => request.post(`/api/admin/stock/imports/${id}/confirm`),
  setStatus: (id, productStatus) => request.post(`/api/admin/products/${id}/status`, { productStatus })
}
