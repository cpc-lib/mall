import request from '@/utils/request'
export default {
  create: (data) => request.post('/api/refund-applies', data),
  update: (refundNo, data) => request.put(`/api/refund-applies/${refundNo}`, data),
  cancel: (refundNo) => request.delete(`/api/refund-applies/${refundNo}`),
  mine: (page, size) => request.get('/api/refund-applies', { params: { page, size } })
}
