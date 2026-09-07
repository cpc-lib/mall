import assert from 'node:assert/strict'
import { availableRefundQuantity, refundAmount } from '../src/utils/refundQuota.js'
const item = { id: 11, quantity: 10, unitPrice: 2599, refundedQuantity: 2 }
const applies = [
  { apply: { refundNo: 'R1', applyStatus: 'PENDING' }, items: [{ orderItemId: 11, refundQuantity: 3 }] },
  { apply: { refundNo: 'R2', applyStatus: 'ACCEPTED' }, items: [{ orderItemId: 11, refundQuantity: 1 }] },
  { apply: { refundNo: 'R3', applyStatus: 'CANCELLED' }, items: [{ orderItemId: 11, refundQuantity: 9 }] }
]
assert.equal(availableRefundQuantity(item, applies), 4)
assert.equal(availableRefundQuantity(item, applies, 'R1'), 7)
assert.equal(refundAmount(item.unitPrice, 3), 7797)
const asyncWindow = [{ apply: { refundNo: 'R4', applyStatus: 'SUCCESS' }, items: [{ orderItemId: 11, refundQuantity: 4 }] }]
assert.equal(availableRefundQuantity({ ...item, refundedQuantity: 0 }, asyncWindow), 6)
console.log('refund-quota.test: PASS')
