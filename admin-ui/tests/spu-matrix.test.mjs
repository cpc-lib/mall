import test from 'node:test'
import assert from 'node:assert/strict'
import { buildSkuMatrix, parseSpecValuesInput } from '../src/utils/spuMatrix.js'

test('parseSpecValuesInput splits comma/Chinese comma/newline and deduplicates', () => {
  assert.deepEqual(parseSpecValuesInput('标准版, 进阶版，标准版\n旗舰版'), ['标准版', '进阶版', '旗舰版'])
})

test('buildSkuMatrix generates Cartesian product and preserves existing SKUs', () => {
  const groups = [
    { specName: '版本', valuesText: '标准版, 进阶版' },
    { specName: '有效期', valuesText: '1年, 永久' }
  ]
  const existing = [
    {
      skuId: 501,
      skuCode: 'SKU-STD-1Y',
      price: 19900,
      stock: 42,
      productStatus: 'ENABLED',
      specs: [{ specName: '版本', specValue: '标准版' }, { specName: '有效期', specValue: '1年' }]
    }
  ]
  const matrix = buildSkuMatrix(groups, existing)
  assert.equal(matrix.length, 4)
  assert.equal(matrix[0].skuId, 501)
  assert.equal(matrix[0].price, 19900)
  assert.equal(matrix[0].specText, '标准版 / 1年')
  assert.equal(matrix[1].skuId, null)
  assert.equal(matrix[1].specText, '标准版 / 永久')
})
