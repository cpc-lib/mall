# Vue Header Logout Shortcut Spec

## 0. Metadata

- Status: implemented
- Domain: frontend
- Updated: 2026-09-05
- Owner: TBD
- Related work: add a header "退出登录" shortcut for logged-in Vue users
- Issue classification: Design change
- Impact scope: Vue frontend header navigation only

## 1. Background

Logout existed only inside the 用户中心 page (`/account`), so users had to navigate into the account page to sign out. This change adds a direct "退出登录" entry in the shared app header, visible only when logged in. The auth backend endpoints and the existing `/account` logout button are unchanged.

## 2. Contract

- When no access token exists, the header keeps showing `登录 / 注册` and does not show `退出登录`.
- When a user is logged in, the header nav shows `退出登录` after the admin entries.
- Clicking `退出登录` calls `POST /api/auth/logout` with the stored refresh token, then clears the local auth store and redirects to `/login`, regardless of whether the logout request succeeds.
- The nav entry re-renders through the existing `payment-auth-changed` event; no new state store or API wrapper is introduced.

## 3. Acceptance Criteria

- [x] `AppHeader.vue` renders the `退出登录` nav entry only when `getAccessToken()` is non-empty.
- [x] Logout flow calls `authApi.logout(getRefreshToken())`, `clearAuth()`, and routes to `/login`.
- [x] The `/account` page logout button keeps working unchanged.
- [x] `npm run build` passes.

## 4. Implementation Anchors

| Area | Anchor |
|---|---|
| Header nav entry and logout method | `payment-demo-vue/src/components/AppHeader.vue` |
| Auth API wrapper (existing) | `payment-demo-vue/src/api/auth.js` |
| Auth store helpers (existing) | `payment-demo-vue/src/utils/authStore.js` |
| Spec ledger | `spec/README.md` |

## 5. Compatibility Impact

No backend HTTP route, request shape, response structure, response text, database schema, provider callback, event name, or config key changed. The frontend reuses the existing `/api/auth/logout` call already used by `Account.vue`; the only visible change is the additional header nav entry.

## 6. Verification

```powershell
npm run build
```

Result on 2026-09-05: build completed successfully; webpack reported the existing bundle-size warnings only.

Runtime endpoint probe on 2026-09-05: `POST /api/auth/logout` and `POST /api/auth/password` on the running backend returned HTTP 401 with `未登录或 Access Token 缺失` when called without a token, confirming the auth contract the shortcut relies on.

## 7. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-09-05 | implemented | Added header logout shortcut to `AppHeader.vue`. | User request: 退出登录与修改密码功能核对 |
