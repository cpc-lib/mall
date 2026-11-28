import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildSpecKeyFromSelection,
  findMatchingSku,
  formatPriceRange,
  getDefaultSelection,
  isSpecOptionPurchasable,
  parseSpecKey
} from '../src/utils/skuSpec.js'

const specGroups = [
  { id: 101, specName: '版本', values: [{ id: 1001, specValue: '标准版' }, { id: 1002, specValue: '进阶版' }] },
  { id: 102, specName: '有效期', values: [{ id: 1003, specValue: '1年有效' }, { id: 1004, specValue: '永久有效' }] }
]

const skus = [
  { id: 1, specKey: '101:1001;102:1003', price: 19900, stock: 10, productStatus: 'ENABLED', isDefault: true },
  { id: 2, specKey: '101:1001;102:1004', price: 29900, stock: 0, productStatus: 'ENABLED', isDefault: false },
  { id: 3, specKey: '101:1002;102:1003', price: 49900, stock: 5, productStatus: 'DISABLED', isDefault: false },
  { id: 4, specKey: '101:1002;102:1004', price: 69900, stock: 8, productStatus: 'ENABLED', isDefault: false }
]

test('parseSpecKey & buildSpecKeyFromSelection normalize order', () => {
  assert.deepEqual(parseSpecKey('DEFAULT'), {})
  assert.deepEqual(parseSpecKey('101:1001;102:1003'), { 101: '1001', 102: '1003' })
  assert.equal(buildSpecKeyFromSelection({ 102: '1004', 101: '1002' }), '101:1002;102:1004')
})

test('findMatchingSku resolves multi-spec and single-spec SKUs', () => {
  const matched = findMatchingSku(skus, { 101: '1002', 102: '1004' }, specGroups)
  assert.equal(matched?.id, 4)
  assert.equal(findMatchingSku(skus, { 101: '1002' }, specGroups), null)
  const single = findMatchingSku([skus[0]], {}, [])
  assert.equal(single?.id, 1)
})

test('isSpecOptionPurchasable checks ENABLED + stock > 0 under current sibling selection', () => {
  assert.equal(isSpecOptionPurchasable(skus, { 101: '1001' }, 102, 1003), true)
  assert.equal(isSpecOptionPurchasable(skus, { 101: '1001' }, 102, 1004), false) // stock=0
  assert.equal(isSpecOptionPurchasable(skus, { 101: '1002' }, 102, 1003), false) // DISABLED
  assert.equal(isSpecOptionPurchasable(skus, { 101: '1002' }, 102, 1004), true)
})

test('getDefaultSelection & formatPriceRange', () => {
  assert.deepEqual(getDefaultSelection(specGroups, skus, 1), { 101: '1001', 102: '1003' })
  assert.equal(formatPriceRange(19900, 69900), '¥199.00 - ¥699.00')
  assert.equal(formatPriceRange(19900, 19900), '¥199.00')
})
