import assert from 'node:assert/strict'
import test from 'node:test'
import { validateStockAoa } from '../src/utils/stockAoaCore.js'

test('validateStockAoa 跳过表头、空行与无调整量行，收集合法项与非法行问题', () => {
  const aoa = [
    ['商品ID', '商品名称', '调整量'],   // 表头，跳过
    [1, '商品A', 5],                     // 合法
    [2, '商品B', -3],                    // 合法（负向调整）
    ['bad', '商品C', 2],                 // 非法：非整数商品ID
    [3, '商品D', ''],                    // 无调整量，跳过
    [4, '商品E', 0],                     // 非法：调整量为 0
    [5, '商品F', 1.5],                   // 非法：调整量非整数
    [],                                  // 空行
    [6, '商品G', 10]                     // 合法
  ]
  const { items, issues } = validateStockAoa(aoa)
  assert.deepEqual(items, [
    { productId: 1, delta: 5 },
    { productId: 2, delta: -3 },
    { productId: 6, delta: 10 }
  ])
  assert.equal(issues.length, 3, `应报告 3 条问题，实际: ${issues}`)
  assert.ok(issues.some(i => i.includes('第4行')))
  assert.ok(issues.some(i => i.includes('第7行')))
})

test('validateStockAoa 数字字符串 ID 解析为整数', () => {
  const { items, issues } = validateStockAoa([['123', 'X', '7']])
  assert.equal(issues.length, 0)
  assert.deepEqual(items, [{ productId: 123, delta: 7 }])
})

test('validateStockAoa 空 AOA 返回空结果', () => {
  const { items, issues } = validateStockAoa([])
  assert.deepEqual(items, [])
  assert.deepEqual(issues, [])
})
