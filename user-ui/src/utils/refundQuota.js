// V2 交易模型：退款金额由服务端按订单快照计算（尾差规则），客户端仅做
// 数量上限与金额预估展示，不作为提交依据。预估口径与服务端 RefundPolicy 一致：
//   可退数量 = quantity - refundedQty - refundFrozenQty
//   金额 = 退完剩余全部 → 剩余可退金额；否则 floor(payAmount / quantity) × 件数

export function availableRefundQuantity(orderItem) {
  const purchased = Number(orderItem?.quantity || 0)
  const refunded = Number(orderItem?.refundedQty || 0)
  const frozen = Number(orderItem?.refundFrozenQty || 0)
  return Math.max(0, purchased - refunded - frozen)
}

/** 编辑场景：当前退款单自身冻结的数量不计入占用（重新提交时替换原冻结）。 */
export function availableRefundQuantityExcluding(orderItem, excludeQty = 0) {
  return Math.max(0, availableRefundQuantity(orderItem) + Number(excludeQty || 0))
}

export function refundAmountEstimate(orderItem, qty) {
  const n = Number(qty || 0)
  if (n <= 0) return 0
  const purchased = Number(orderItem?.quantity || 0)
  if (purchased <= 0) return 0
  const payAmount = Number(orderItem?.payAmount || 0)
  const remainingQty = availableRefundQuantity(orderItem)
  const remainingAmount = Math.max(0, payAmount - Number(orderItem?.refundedAmount || 0))
  if (n >= remainingQty) return remainingAmount
  return Math.floor(payAmount / purchased) * n
}
