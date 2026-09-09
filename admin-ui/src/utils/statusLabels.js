export const PAY_LABEL = { UNPAID: '待支付', PAID: '已支付' }
export const FULFILLMENT_LABEL = { WAIT_SHIP: '待发货', SHIPPED: '已发货', RECEIVED: '已收货', CANCELLED: '已取消' }
export const ORDER_LIFECYCLE_LABEL = { WAIT_PAY: '待支付', ACTIVE: '已激活', CLOSED: '已关闭' }
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
export const REFUND_STATUS_COLOR = {
  APPLYING: 'blue', APPROVED: 'cyan', REJECTED: 'default', CANCELLED: 'default',
  REFUNDING: 'orange', SUCCESS: 'green', FAILED: 'red'
}
export const PAYMENT_STATUS_COLOR = { SUCCESS: 'green', PAYING: 'orange' }
export const RESET_STATUS_LABEL = { PENDING: '待处理', HANDLED: '已处理', REJECTED: '已拒绝' }
export const RESET_STATUS_COLOR = { PENDING: 'orange', HANDLED: 'green', REJECTED: 'default' }
export const ROLE_COLOR = { ROLE_ADMIN: 'red', ROLE_USER: 'blue' }
export const USER_STATUS_LABEL = { ENABLED: '正常', DISABLED: '已禁用' }
export const USER_STATUS_COLOR = { ENABLED: 'green', DISABLED: 'red' }
export const PRODUCT_STATUS_COLOR = { ENABLED: 'green', DISABLED: 'default' }
export const OPERATION_STATUS_LABEL = { SUCCESS: '成功', FAILED: '失败' }
export const OPERATION_STATUS_COLOR = { SUCCESS: 'green', FAILED: 'red' }
export const STOCK_IMPORT_STATUS_LABEL = { PENDING: '待确认', CONFIRMED: '已入库' }
export const STOCK_IMPORT_STATUS_COLOR = { PENDING: 'orange', CONFIRMED: 'default' }
export const PAY_COLOR = { UNPAID: 'orange', PAID: 'green' }
export const ORDER_REFUND_COLOR = { REFUNDING: 'red' }
export const CONFIG_STATUS_COLOR = { ENABLED: 'success', DISABLED: 'default' }

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
