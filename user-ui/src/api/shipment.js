import request from '@/utils/request'
export default {
  getShipment: (orderNo) => request.get(`/api/order/${orderNo}/shipment`),
  confirmReceipt: (orderNo) => request.post(`/api/order/${orderNo}/confirm-receipt`),
  cancelPaidOrder: (orderNo) => request.post(`/api/order/${orderNo}/cancel`)
}
