import request from '@/utils/request'
const create = (data, idempotencyKey) => request.post('/api/checkout/orders', data, {
  headers: idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : undefined
})
export default {
  create,
  list: (params) => request.get('/api/checkout/orders', { params }),
  get: (orderNo) => request.get(`/api/checkout/orders/${orderNo}`),
  payQuery: (orderNo) => request.get(`/api/checkout/orders/${orderNo}/pay-query`),
  alipay: (orderNo) => request.post(`/api/checkout/orders/${orderNo}/alipay`),
  wxpay: (orderNo) => request.post(`/api/checkout/orders/${orderNo}/wxpay`)
}
