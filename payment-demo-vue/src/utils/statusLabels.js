// V2 交易模型状态中文展示映射（与后端枚举英文落库值对应）。
export const PAY_LABEL = { UNPAID: '待支付', PAID: '已支付' }
export const FULFILLMENT_LABEL = { WAIT_SHIP: '待发货', SHIPPED: '已发货', RECEIVED: '已收货', CANCELLED: '已取消' }
export const ORDER_REFUND_LABEL = { NONE: '', REFUNDING: '退款中', PARTIAL_REFUNDED: '部分退款', FULL_REFUNDED: '已全额退款' }
export const SHIPMENT_LABEL = { SHIPPED: '已发货', IN_TRANSIT: '运输中', DELIVERED: '已送达', RECEIVED: '已收货', CANCELLED: '已取消' }
export const REFUND_TYPE_LABEL = {
  CANCEL_BEFORE_SHIP: '未发货取消（补库存）', RETURN_AND_REFUND: '退货退款', REFUND_ONLY: '仅退款',
  PRICE_ADJUSTMENT: '差价退款', DUPLICATE_PAYMENT: '重复支付退款', LATE_PAYMENT: '晚到支付退款'
}
export const REFUND_STATUS_LABEL = {
  APPLYING: '待审核', APPROVED: '已受理', REJECTED: '已拒绝', CANCELLED: '已撤销',
  REFUNDING: '退款中', SUCCESS: '退款成功', FAILED: '退款失败'
}

/** V2 四维状态标签；旧数据缺新字段时回退 legacy orderStatus。 */
export function statusTags(order) {
  if (order.payStatus || order.fulfillmentStatus || order.refundStatus) {
    return [
      PAY_LABEL[order.payStatus] || order.payStatus,
      FULFILLMENT_LABEL[order.fulfillmentStatus] || order.fulfillmentStatus,
      ORDER_REFUND_LABEL[order.refundStatus]
    ].filter(Boolean)
  }
  return [order.orderStatus]
}
