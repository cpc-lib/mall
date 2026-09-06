import assert from 'node:assert/strict'
let calls = 0
function createRefreshSingleFlight(refreshFactory) { let inFlight = null; return function () { if (!inFlight) inFlight = Promise.resolve().then(refreshFactory).finally(() => { inFlight = null }); return inFlight } }
const refresh = createRefreshSingleFlight(async () => { calls += 1; await new Promise(r => setTimeout(r, 10)); return 'token' })
const results = await Promise.all(Array.from({ length: 20 }, () => refresh()))
assert.equal(calls, 1); assert.equal(new Set(results).size, 1); console.log('vue refresh-single-flight: PASS')
