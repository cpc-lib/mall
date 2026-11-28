import request from '@/utils/request'
export default {
  productPage: (params) => request.get('/api/admin/products/page', { params }),
  productsByIds: (ids) => request.post('/api/admin/products/batch', ids),
  getProduct: (id) => request.get(`/api/admin/products/${id}`),
  createProduct: (data) => request.post('/api/admin/products', data),
  adjustStock: (id, delta) => request.post(`/api/admin/products/${id}/stock`, { delta }),
  batchAdjustStock: (items) => request.post('/api/admin/products/stock/batch', { items }),
  transactions: (params) => request.get('/api/admin/stock/transactions', { params }),
  imports: (page, size) => request.get('/api/admin/stock/imports', { params: { page, size } }),
  getImport: (id) => request.get(`/api/admin/stock/imports/${id}`),
  createImport: (data) => request.post('/api/admin/stock/imports', data),
  getImportFile: (id) => request.get(`/api/admin/stock/imports/${id}/file`),
  saveImportFile: (id, data) => request.put(`/api/admin/stock/imports/${id}/file`, data),
  confirmImport: (id) => request.post(`/api/admin/stock/imports/${id}/confirm`),
  setStatus: (id, productStatus) => request.post(`/api/admin/products/${id}/status`, { productStatus }),
  spuPage: (params) => request.get('/api/admin/products/spu/page', { params }),
  getSpu: (spuId) => request.get(`/api/admin/products/spu/${spuId}`),
  createSpu: (data) => request.post('/api/admin/products/spu', data),
  updateSpu: (spuId, data) => request.put(`/api/admin/products/spu/${spuId}`, data),
  setSpuStatus: (spuId, productStatus) => request.post(`/api/admin/products/spu/${spuId}/status`, { productStatus })
}
