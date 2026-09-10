import request from '@/utils/request'
export default {
  create: (data) => request.post('/api/refund-applies', data),
  update: (refundNo, data) => request.put(`/api/refund-applies/${refundNo}`, data),
  cancel: (refundNo) => request.delete(`/api/refund-applies/${refundNo}`),
  mine: () => request.get('/api/refund-applies'),
  all: () => request.get('/api/refund-applies/admin/all'),
  accept: (refundNo, remark, goodsDisposition) => request.post(`/api/refund-applies/${refundNo}/accept`, { remark, goodsDisposition }),
  reject: (refundNo, remark) => request.post(`/api/refund-applies/${refundNo}/reject`, { remark }),
  confirmReturn: (refundNo, remark) => request.post(`/api/admin/refund/${refundNo}/confirm-return`, { remark }),
  retry: (refundNo) => request.post(`/api/admin/refund/${refundNo}/retry`),
  queryStatus: (refundNo) => request.post(`/api/admin/refund/${refundNo}/query-status`),
  priceAdjustment: (data) => request.post('/api/admin/refund/price-adjustment', data)
}
