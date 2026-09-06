import request from '@/utils/request'
export default {
  list: () => request.get('/api/cart'),
  put: (data) => request.post('/api/cart/item', data),
  select: (productId, selected) => request.post('/api/cart/select', { productId, selected }),
  remove: (productId) => request.delete(`/api/cart/item/${productId}`)
}
