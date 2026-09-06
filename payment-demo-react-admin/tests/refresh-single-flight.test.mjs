import assert from 'node:assert/strict'
import test from 'node:test'
import { createRefreshSingleFlight } from '../src/utils/refreshSingleFlight.js'

test('20 concurrent 401 refresh requests share one refresh call', async () => {
  let calls = 0
  const refresh = createRefreshSingleFlight(async () => {
    calls += 1
    await new Promise(resolve => setTimeout(resolve, 20))
    return 'new-access-token'
  })
  const result = await Promise.all(Array.from({ length: 20 }, () => refresh()))
  assert.equal(calls, 1)
  assert.equal(new Set(result).size, 1)
  assert.equal(result[0], 'new-access-token')
})
