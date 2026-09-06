import request from '@/utils/request'
export default {
  getShipment: (orderNo) => request.get(`/api/order/${orderNo}/shipment`),
  confirmReceipt: (orderNo) => request.post(`/api/order/${orderNo}/confirm-receipt`),
  cancelPaidOrder: (orderNo) => request.post(`/api/order/${orderNo}/cancel`),
  waitShipList: () => request.get('/api/admin/order/wait-ship'),
  shipOrder: (orderNo) => request.post(`/api/admin/order/${orderNo}/ship`),
  allOrders: (params) => request.get('/api/admin/order/all', { params }),
  forceClose: (orderNo) => request.post(`/api/admin/order/${orderNo}/force-close`),
  markPaid: (orderNo) => request.post(`/api/admin/order/${orderNo}/mark-paid`)
}
