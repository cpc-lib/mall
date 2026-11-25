import assert from 'node:assert/strict'
import test from 'node:test'
import {
  availableRefundQuantity,
  availableRefundQuantityExcluding,
  refundAmountEstimate
} from '../src/utils/refundQuota.js'

// 与后端 RefundPolicy 保持一致：
//   refQty = max(0, quantity - refunded - frozen)
//   金额预估：退完剩余全额，否则 floor(payAmount/quantity) * n
//   尾差规则：最后一件取剩余全额

test('可退数量 = 购买量 - 已退 - 冻结，下限为 0', () => {
  assert.equal(availableRefundQuantity({ quantity: 3, refundedQty: 1, refundFrozenQty: 1 }), 1)
  assert.equal(availableRefundQuantity({ quantity: 3, refundedQty: 5, refundFrozenQty: 5 }), 0, '脏数据不得为负')
  assert.equal(availableRefundQuantity({ quantity: 0 }), 0)
  assert.equal(availableRefundQuantity(null), 0)
})

test('excluding 当前冻结数量时加回 excludeQty', () => {
  const item = { quantity: 3, refundedQty: 1, refundFrozenQty: 1 }
  // 正常剩 1；编辑时当前单冻结 1 件，所以可编辑范围应为 1 + 1 = 2
  assert.equal(availableRefundQuantityExcluding(item, 1), 2)
})

test('非最后一件按 floor(pay/quantity) 计算，最后一件吃尾差', () => {
  // 行实付 1000 买了 3 件，unitPrice 1999（优惠后 payAmount=1000）
  const item = { quantity: 3, payAmount: 1000, refundedQty: 0, refundedAmount: 0 }
  assert.equal(refundAmountEstimate(item, 1), 333, 'floor(1000/3)*1')
  assert.equal(refundAmountEstimate(item, 2), 666, 'floor(1000/3)*2')
  // 退 3 件 = 剩余全部可退金额（尾差规则）
  assert.equal(refundAmountEstimate(item, 3), 1000)
})

test('全部退完累计等于行实付金额', () => {
  const item = { quantity: 3, payAmount: 1999, refundedQty: 0, refundedAmount: 0 }
  const first = refundAmountEstimate(item, 1) // 666
  const afterFirst = { ...item, refundedQty: 1, refundedAmount: first }
  const second = refundAmountEstimate(afterFirst, 2) // 剩余 2 件 = 1999-666 = 1333
  assert.equal(first + second, 1999)
})

test('边界：数量非法/超过可退数量返回 0 或剩余全额', () => {
  const item = { quantity: 3, payAmount: 1000, refundedQty: 0, refundedAmount: 0 }
  assert.equal(refundAmountEstimate(item, 0), 0)
  assert.equal(refundAmountEstimate(item, -1), 0)
  // 超过剩余数量 → 返回剩余全额
  assert.equal(refundAmountEstimate(item, 99), 1000)
})
