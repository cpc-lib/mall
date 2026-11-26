import assert from 'node:assert/strict'
import test from 'node:test'

class MemoryStorage {
  constructor(seed = {}) { this.values = new Map(Object.entries(seed)) }
  getItem(key) { return this.values.has(key) ? this.values.get(key) : null }
  setItem(key, value) { this.values.set(key, String(value)) }
  removeItem(key) { this.values.delete(key) }
}

let importSequence = 0
async function loadAuthStore(t, seed = {}) {
  const storage = new MemoryStorage(seed)
  globalThis.localStorage = storage
  t.after(() => { delete globalThis.localStorage })
  const store = await import(`../src/utils/authStore.js?test=${++importSequence}`)
  return { store, storage }
}

test('保存、读取与清理会话只影响商城端命名空间', async t => {
  const { store, storage } = await loadAuthStore(t, { unrelated: 'keep' })

  store.saveAuth({
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    user: { id: 7, username: 'alice', role: 'ROLE_USER' }
  })

  assert.equal(store.getAccessToken(), 'access-1')
  assert.equal(store.getRefreshToken(), 'refresh-1')
  assert.deepEqual(store.getUser(), { id: 7, username: 'alice', role: 'ROLE_USER' })

  store.clearAuth()
  assert.equal(store.getAccessToken(), '')
  assert.equal(store.getRefreshToken(), '')
  assert.equal(store.getUser(), null)
  assert.equal(storage.getItem('unrelated'), 'keep')
})

test('损坏的用户 JSON 安全返回 null', async t => {
  const { store } = await loadAuthStore(t, { mall_user: '{broken' })
  assert.equal(store.getUser(), null)
})

test('承接商城旧前缀会话，但不迁移共享前缀中的管理员会话', async t => {
  const legacy = await loadAuthStore(t, {
    payment_demo_mall_access_token: 'legacy-access',
    payment_demo_mall_refresh_token: 'legacy-refresh',
    payment_demo_mall_user: JSON.stringify({ id: 8, role: 'ROLE_USER' })
  })
  assert.equal(legacy.store.getAccessToken(), 'legacy-access')
  assert.equal(legacy.store.getRefreshToken(), 'legacy-refresh')

  const admin = await loadAuthStore(t, {
    payment_demo_access_token: 'admin-access',
    payment_demo_user: JSON.stringify({ id: 1, role: 'ROLE_ADMIN' })
  })
  assert.equal(admin.store.getAccessToken(), '')
})
