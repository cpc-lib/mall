import request from '@/utils/request'
export default {
  create: (data) => request.post('/api/checkout/orders', data),
  list: () => request.get('/api/checkout/orders'),
  get: (orderNo) => request.get(`/api/checkout/orders/${orderNo}`),
  alipay: (orderNo) => request.post(`/api/checkout/orders/${orderNo}/alipay`),
  wxpay: (orderNo) => request.post(`/api/checkout/orders/${orderNo}/wxpay`)
}
