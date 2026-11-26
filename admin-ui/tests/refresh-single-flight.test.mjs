import assert from 'node:assert/strict'
import test from 'node:test'
import { createRefreshSingleFlight } from '../src/utils/refreshSingleFlight.js'

test('并发刷新共享同一次请求与结果', async () => {
  let calls = 0
  const refresh = createRefreshSingleFlight(async () => {
    calls += 1
    await new Promise(resolve => setTimeout(resolve, 20))
    return 'new-access-token'
  })

  const results = await Promise.all(Array.from({ length: 20 }, () => refresh()))

  assert.equal(calls, 1)
  assert.deepEqual(new Set(results), new Set(['new-access-token']))
})

test('刷新失败后清理在途状态，后续调用可以重试', async () => {
  let calls = 0
  const refresh = createRefreshSingleFlight(async () => {
    calls += 1
    if (calls === 1) throw new Error('network error')
    return 'recovered-token'
  })

  await assert.rejects(refresh(), /network error/)
  assert.equal(await refresh(), 'recovered-token')
  assert.equal(calls, 2)
})
