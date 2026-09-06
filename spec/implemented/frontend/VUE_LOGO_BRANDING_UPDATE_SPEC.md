# Vue Logo Branding Update Spec

## 0. Metadata

- Status: implemented
- Domain: frontend
- Updated: 2026-06-12
- Owner: TBD
- Related work: replace Vue header logo text with `苏三的开发日记`
- Issue classification: Design change
- Impact scope: Vue frontend header logo image and accessible logo text

## 1. Background

The Vue frontend displayed the previous `logo.png` brand text in the shared app header. This change replaces the visible logo name with `苏三的开发日记` while keeping the existing logo asset path and header integration stable.

## 2. Contract

- `payment-demo-vue/src/assets/img/logo.png` remains the logo image consumed by the Vue header.
- The logo image displays the exact text `苏三的开发日记`.
- The image keeps the existing transparent PNG format and fits the current header logo container.
- The Vue header link title and image alt text match the displayed logo name.

## 3. Acceptance Criteria

- [x] `logo.png` displays `苏三的开发日记`.
- [x] The existing `logo.png` path remains unchanged.
- [x] `AppHeader.vue` logo title and alt text use `苏三的开发日记`.
- [x] Vue frontend verification passes or any failure is documented.

## 4. Implementation Anchors

| Area | Anchor |
|---|---|
| Vue logo image | `payment-demo-vue/src/assets/img/logo.png` |
| Vue header logo metadata | `payment-demo-vue/src/components/AppHeader.vue` |
| Spec ledger | `spec/README.md` |

## 5. Compatibility Impact

This intentionally changes the Vue frontend's visible and accessible logo name from the previous brand text to `苏三的开发日记`.

No backend HTTP route, request shape, response structure, response text, database schema, provider callback, event name, config key, or frontend API call changed.

## 6. Verification

```powershell
npm run build
npm run lint
```

Result on 2026-06-12: both commands passed. Webpack reported existing bundle size performance warnings for `chunk-vendors` and the `app` entrypoint.

Runtime check on 2026-06-12: local Vue dev server at `http://localhost:3001/` returned HTTP 200, and the served app script contained `苏三的开发日记`.

## 7. Change Log

| Date | Status | Change | Related Work |
|---|---|---|---|
| 2026-06-12 | implemented | Replaced Vue logo brand text and matching header metadata. | User request |
